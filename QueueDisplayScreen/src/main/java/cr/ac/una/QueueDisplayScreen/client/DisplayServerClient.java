package cr.ac.una.QueueDisplayScreen.client;

import cr.ac.una.QueueDisplayScreen.network.NetworkSettings;
import cr.ac.una.QueueDisplayScreen.model.DisplaySnapshot;
import cr.ac.una.QueueDisplayScreen.model.TicketRow;
import cr.ac.una.QueueDisplayScreen.service.DisplayRefreshService.ChangeSubscription;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Consulta capturas públicas y abre un canal persistente para recibir avisos de cambios.
// Valida los campos del protocolo antes de convertirlos en filas de pantalla.
public class DisplayServerClient {

    private static final int HEADER_FIELDS = 5;
    private static final int MAX_WAITING_ROWS = 100;
    private static final int TICKET_FIELDS = 9;

    private final String host;
    private final int port;

    public DisplayServerClient(String host, int port) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("La dirección del servidor es obligatoria.");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("El puerto no es válido.");
        }
        this.host = host;
        this.port = port;
    }

    public DisplaySnapshot getSnapshot() throws IOException {
        return parseSnapshot(requestSnapshot());
    }

    // Conserva el socket solo después de recibir la confirmación de suscripción.
    public ChangeSubscription subscribe() throws IOException {
        Socket socket = NetworkSettings.open(host, port, 3000, 25000);
        try {
            BufferedReader input = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
            );
            BufferedWriter output = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
            );
            output.write("SUBSCRIBE_DISPLAY");
            output.newLine();
            output.flush();

            if (!"OK;SUBSCRIBED".equals(input.readLine())) {
                throw new IOException("El servidor no confirmó la suscripción de la pantalla.");
            }
            return new ServerSubscription(socket, input);

        } catch (IOException | RuntimeException exception) {
            try {
                socket.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    private static final class ServerSubscription implements ChangeSubscription {

        private final Socket socket;
        private final BufferedReader input;

        private ServerSubscription(Socket socket, BufferedReader input) {
            this.socket = socket;
            this.input = input;
        }

        // Distingue un cambio de un latido; un cierre o mensaje desconocido exige reconectar.
        @Override
        public boolean awaitChange() throws IOException {
            String message = input.readLine();
            if ("EVENT;DISPLAY_CHANGED".equals(message)) {
                return true;
            }
            if ("OK;HEARTBEAT".equals(message)) {
                return false;
            }
            throw new IOException("Se perdió el canal de avisos o se recibió un mensaje inválido.");
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    private String requestSnapshot() throws IOException {
        try (Socket socket = NetworkSettings.open(host, port, 3000, 15000)) {

            try (
                    BufferedWriter output = new BufferedWriter(
                            new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
                    ); BufferedReader input = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
                    )) {
                output.write("GET_DISPLAY_SNAPSHOT_V3");
                output.newLine();
                output.flush();
                return input.readLine();
            }
        }
    }

    // Comprueba cantidades y estructura antes de leer las filas. V3 envía
    // OK;DISPLAY_SNAPSHOT_V3;<llamados>;<espera enviada>;<total en espera>, después todos los
    // llamados/en atención y luego la espera; cada ticket ocupa nueve campos.
    private DisplaySnapshot parseSnapshot(String line) throws IOException {
        if (line == null || line.isBlank()) {
            throw new IOException("El servidor no envió información para la pantalla.");
        }

        String[] fields = line.split(";", -1);
        try {
            for (int index = 0; index < fields.length; index++) {
                fields[index] = URLDecoder.decode(fields[index], StandardCharsets.UTF_8);
            }
        } catch (IllegalArgumentException exception) {
            throw new IOException("La respuesta tiene una codificación inválida.", exception);
        }

        if (fields.length == 2 && "ERROR".equals(fields[0])) {
            throw new IOException(fields[1]);
        }
        if (fields.length < HEADER_FIELDS || !"OK".equals(fields[0])
                || !"DISPLAY_SNAPSHOT_V3".equals(fields[1])) {
            throw new IOException("La respuesta de la pantalla no es válida.");
        }

        // Los llamados no tienen un máximo fijo (uno por asignación abierta); su cantidad queda
        // acotada por la propia longitud de la respuesta, que se comprueba con aritmética long.
        int calledCount = parseCount(fields[2], Integer.MAX_VALUE);
        int waitingCount = parseCount(fields[3], MAX_WAITING_ROWS);
        int waitingTotal = parseTotal(fields[4], waitingCount);
        long expectedFields = HEADER_FIELDS + (long) TICKET_FIELDS * ((long) calledCount + waitingCount);
        if (fields.length != expectedFields) {
            throw new IOException("La información de la pantalla está incompleta.");
        }

        Set<Long> ticketIds = new HashSet<>();
        List<TicketRow> calledTickets = readTickets(
                fields, HEADER_FIELDS, calledCount, false, ticketIds
        );
        List<TicketRow> waitingTickets = readTickets(
                fields, HEADER_FIELDS + TICKET_FIELDS * calledCount, waitingCount, true, ticketIds
        );

        try {
            return new DisplaySnapshot(calledTickets, waitingTickets, waitingTotal);
        } catch (IllegalArgumentException exception) {
            throw new IOException("La captura de la pantalla no es coherente.", exception);
        }
    }

    // El total en espera puede superar el límite de filas enviadas, pero nunca ser menor.
    private int parseTotal(String value, int sentCount) throws IOException {
        try {
            int total = Integer.parseInt(value);
            if (total < sentCount) {
                throw new IllegalArgumentException("Total menor que la cantidad enviada.");
            }
            return total;
        } catch (IllegalArgumentException exception) {
            throw new IOException("El total de turnos recibido no es válido.", exception);
        }
    }

    private int parseCount(String value, int max) throws IOException {
        try {
            int count = Integer.parseInt(value);
            if (count < 0 || count > max) {
                throw new IllegalArgumentException("Cantidad fuera de rango.");
            }
            return count;
        } catch (IllegalArgumentException exception) {
            throw new IOException("La cantidad de turnos recibida no es válida.", exception);
        }
    }

    private List<TicketRow> readTickets(
            String[] fields, int offset, int count,
            boolean waiting, Set<Long> ticketIds
    ) throws IOException {
        List<TicketRow> tickets = new ArrayList<>();

        for (int index = 0; index < count; index++) {
            TicketRow ticket = readTicket(fields, offset + TICKET_FIELDS * index);
            // La lista de llamados solo admite LLAMADO y EN_ATENCION: un FINALIZADO nunca se
            // presenta en la pantalla pública aunque un servidor lo enviara.
            boolean validState = waiting
                    ? "EN_ESPERA".equals(ticket.state())
                    : "LLAMADO".equals(ticket.state()) || "EN_ATENCION".equals(ticket.state());
            if (!validState) {
                throw new IOException("Un turno pertenece a una lista incorrecta.");
            }
            if (!ticketIds.add(ticket.ticketId())) {
                throw new IOException("La respuesta contiene un turno repetido.");
            }
            tickets.add(ticket);
        }

        return tickets;
    }

    private TicketRow readTicket(String[] fields, int offset) throws IOException {
        try {
            String priorityText = fields[offset + 3];
            if (!"true".equals(priorityText) && !"false".equals(priorityText)) {
                throw new IllegalArgumentException("La prioridad no es válida.");
            }

            Integer windowId = fields[offset + 5].isEmpty()
                    ? null : Integer.valueOf(fields[offset + 5]);
            LocalDateTime calledAt = fields[offset + 6].isEmpty()
                    ? null : LocalDateTime.parse(fields[offset + 6]);

            return new TicketRow(
                    Long.parseLong(fields[offset]),
                    fields[offset + 1],
                    fields[offset + 2],
                    Boolean.parseBoolean(priorityText),
                    fields[offset + 4],
                    windowId,
                    calledAt,
                    fields[offset + 7].isEmpty() ? null : LocalDateTime.parse(fields[offset + 7]),
                    Integer.parseInt(fields[offset + 8])
            );
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new IOException("Un turno recibido contiene datos inválidos.", exception);
        }
    }
}
