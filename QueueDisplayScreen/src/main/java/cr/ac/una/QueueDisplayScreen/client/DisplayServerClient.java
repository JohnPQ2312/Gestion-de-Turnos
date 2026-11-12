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

public class DisplayServerClient {

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
                output.write("GET_DISPLAY_SNAPSHOT_V2");
                output.newLine();
                output.flush();
                return input.readLine();
            }
        }
    }

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
        if (fields.length < 4 || !"OK".equals(fields[0])
                || !"DISPLAY_SNAPSHOT_V2".equals(fields[1])) {
            throw new IOException("La respuesta de la pantalla no es válida.");
        }

        int waitingCount = parseCount(fields[2]);
        int recentCallsCount = parseCount(fields[3]);
        if (fields.length != 4 + 9 * (waitingCount + recentCallsCount)) {
            throw new IOException("La información de la pantalla está incompleta.");
        }

        Set<Long> ticketIds = new HashSet<>();
        List<TicketRow> waitingTickets = readTickets(
                fields, 4, waitingCount, true, ticketIds
        );
        List<TicketRow> recentCalls = readTickets(
                fields, 4 + 9 * waitingCount, recentCallsCount, false, ticketIds
        );

        return new DisplaySnapshot(waitingTickets, recentCalls);
    }

    private int parseCount(String value) throws IOException {
        try {
            int count = Integer.parseInt(value);
            if (count < 0 || count > 100) {
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
            TicketRow ticket = readTicket(fields, offset + 9 * index);
            if (waiting != "EN_ESPERA".equals(ticket.state())) {
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
