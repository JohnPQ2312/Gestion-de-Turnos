package cr.ac.una.operatorsystem.client;

import cr.ac.una.operatorsystem.model.ActiveTicket;
import cr.ac.una.operatorsystem.model.CalledTicket;
import cr.ac.una.operatorsystem.model.OperatorAssignment;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class OperatorServerClient {

    private final String host;
    private final int port;

    public OperatorServerClient(String host, int port) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("La dirección del servidor es obligatoria.");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("El puerto no es válido.");
        }
        this.host = host;
        this.port = port;
    }

    public List<OperatorAssignment> listAssignments() throws IOException {
        String[] response = request("LIST_ASSIGNMENTS");

        if (response.length < 3 || !"ASSIGNMENTS".equals(response[1])) {
            throw new IOException("La respuesta de asignaciones no es válida.");
        }

        int count;
        try {
            count = Integer.parseInt(response[2]);
        } catch (NumberFormatException exception) {
            throw new IOException("La cantidad de asignaciones no es válida.", exception);
        }

        if (count < 0 || (response.length - 3) % 3 != 0
                || count != (response.length - 3) / 3) {
            throw new IOException("La lista de asignaciones está incompleta.");
        }

        List<OperatorAssignment> assignments = new ArrayList<>();
        Set<Long> assignmentIds = new HashSet<>();

        for (int index = 3; index < response.length; index += 3) {
            long assignmentId = positiveLong(response[index], "asignación");
            int operatorId = positiveInt(response[index + 1], "operador");
            int windowId = positiveInt(response[index + 2], "ventanilla");

            if (!assignmentIds.add(assignmentId)) {
                throw new IOException("Se recibió una asignación repetida.");
            }

            assignments.add(new OperatorAssignment(assignmentId, operatorId, windowId));
        }

        return List.copyOf(assignments);
    }

    public Optional<CalledTicket> callNext(long assignmentId) throws IOException {
        if (assignmentId <= 0) {
            throw new IllegalArgumentException(
                    "El identificador de la asignación debe ser positivo."
            );
        }

        String[] response = request("CALL_NEXT;" + assignmentId);

        if (response.length == 2 && "NO_TICKET".equals(response[1])) {
            return Optional.empty();
        }

        if (response.length != 6 || !"CALLED".equals(response[1])
                || response[2].isBlank() || response[3].isBlank()) {
            throw new IOException("La confirmación del llamado no es válida.");
        }

        return Optional.of(new CalledTicket(
                response[2], response[3],
                positiveInt(response[4], "operador"),
                positiveInt(response[5], "ventanilla")
        ));
    }

    public Optional<ActiveTicket> getActiveTicket(long assignmentId) throws IOException {
        if (assignmentId <= 0) {
            throw new IllegalArgumentException(
                    "El identificador de la asignación debe ser positivo."
            );
        }

        String[] response = request("GET_ACTIVE_TICKET;" + assignmentId);

        if (response.length == 2 && "NO_ACTIVE_TICKET".equals(response[1])) {
            return Optional.empty();
        }

        if (response.length != 9 || !"ACTIVE_TICKET".equals(response[1])) {
            throw new IOException("La respuesta del turno activo no es válida.");
        }

        long receivedAssignmentId = positiveLong(response[5], "asignación");
        if (receivedAssignmentId != assignmentId) {
            throw new IOException("El turno recibido pertenece a otra asignación.");
        }

        try {
            return Optional.of(new ActiveTicket(
                    positiveLong(response[2], "turno"),
                    response[3], response[4], receivedAssignmentId,
                    positiveInt(response[6], "operador"),
                    positiveInt(response[7], "ventanilla"),
                    response[8]
            ));
        } catch (IllegalArgumentException exception) {
            throw new IOException("El turno activo recibido contiene datos inválidos.", exception);
        }
    }

    private long positiveLong(String value, String field) throws IOException {
        try {
            long number = Long.parseLong(value);
            if (number <= 0) {
                throw new NumberFormatException("El identificador no es positivo.");
            }
            return number;
        } catch (NumberFormatException exception) {
            throw new IOException("El identificador de " + field + " no es válido.", exception);
        }
    }

    private int positiveInt(String value, String field) throws IOException {
        long number = positiveLong(value, field);
        if (number > Integer.MAX_VALUE) {
            throw new IOException("El identificador de " + field + " supera el rango permitido.");
        }
        return (int) number;
    }

    private String[] request(String message) throws IOException {
        String line;

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3000);
            socket.setSoTimeout(15000);

            try (
                    BufferedWriter output = new BufferedWriter(
                            new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
                    ); BufferedReader input = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
                    )) {
                output.write(message);
                output.newLine();
                output.flush();
                line = input.readLine();
            }
        }

        if (line == null || line.isBlank()) {
            throw new IOException("El servidor no envió una respuesta.");
        }

        String[] response = line.split(";", -1);
        try {
            for (int index = 0; index < response.length; index++) {
                response[index] = URLDecoder.decode(response[index], StandardCharsets.UTF_8);
            }
        } catch (IllegalArgumentException exception) {
            throw new IOException("La respuesta tiene una codificación inválida.", exception);
        }

        if (response.length == 2 && "ERROR".equals(response[0])) {
            throw new IllegalArgumentException(response[1]);
        }
        if (response.length < 2 || !"OK".equals(response[0])) {
            throw new IOException("El formato de respuesta no es válido.");
        }

        return response;
    }
}
