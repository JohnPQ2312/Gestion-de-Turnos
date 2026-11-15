package cr.ac.una.operatorsystem.client;

import cr.ac.una.operatorsystem.model.ActiveTicket;
import cr.ac.una.operatorsystem.model.CalledTicket;
import cr.ac.una.operatorsystem.model.OperatorAssignment;
import cr.ac.una.operatorsystem.model.UserSession;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

// Envía las acciones de atención con la sesión del funcionario y comprueba sus respuestas.
public class OperatorServerClient {

    private final ProtocolTransport transport;
    private final UserSession session;

    // Todas las operaciones del operador van con la sesión: AUTH;token;comando.
    public OperatorServerClient(String host, int port, UserSession session) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("La dirección del servidor es obligatoria.");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("El puerto no es válido.");
        }
        this.transport = new ProtocolTransport(host, port, ProtocolTransport.Messages.GENERAL);
        this.session = Objects.requireNonNull(session, "La sesión es obligatoria.");
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

        // V2 confirma el turno completo; CALL_NEXT (V1) se conserva en el servidor para las
        // herramientas de concurrencia. Requiere un CoreServer con CALL_NEXT_V2.
        String[] response = request("CALL_NEXT_V2;" + assignmentId);

        if (response.length == 2 && "NO_TICKET".equals(response[1])) {
            return Optional.empty();
        }

        // OK;CALLED_V2;turno;código;servicio;asignación;funcionario;ventanilla;LLAMADO
        if (response.length != 9 || !"CALLED_V2".equals(response[1])
                || !"LLAMADO".equals(response[8])) {
            throw new IOException("La confirmación del llamado no es válida.");
        }

        long receivedAssignmentId = positiveLong(response[5], "asignación");
        if (receivedAssignmentId != assignmentId) {
            throw new IOException("La confirmación del llamado pertenece a otra asignación.");
        }

        try {
            return Optional.of(new CalledTicket(
                    positiveLong(response[2], "turno"),
                    response[3], response[4], receivedAssignmentId,
                    positiveInt(response[6], "operador"),
                    positiveInt(response[7], "ventanilla")
            ));
        } catch (IllegalArgumentException exception) {
            throw new IOException("La confirmación del llamado contiene datos inválidos.", exception);
        }
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

    public void startAttention(long assignmentId, long ticketId)
            throws IOException {
        requestTransition(
                "START_ATTENTION", "ATTENTION_STARTED",
                assignmentId, ticketId
        );
    }

    public void finishAttention(long assignmentId, long ticketId)
            throws IOException {
        requestTransition(
                "FINISH_ATTENTION", "ATTENTION_FINISHED",
                assignmentId, ticketId
        );
    }

    public void recallTicket(long assignmentId, long ticketId) throws IOException {
        if (assignmentId <= 0 || ticketId <= 0) {
            throw new IllegalArgumentException("Los identificadores del rellamado deben ser positivos.");
        }
        String[] response = request("RECALL_TICKET;" + assignmentId + ";" + ticketId);
        if (response.length != 6 || !"RECALLED".equals(response[1])
                || positiveLong(response[2], "asignación") != assignmentId
                || positiveLong(response[3], "turno") != ticketId
                || positiveInt(response[4], "contador de llamados") < 2) {
            throw new IOException("La confirmación no corresponde al rellamado solicitado.");
        }
        try {
            java.time.LocalDateTime.parse(response[5]);
        } catch (java.time.format.DateTimeParseException exception) {
            throw new IOException("La fecha confirmada del rellamado no es válida.", exception);
        }
    }

    private void requestTransition(
            String command,
            String confirmationType,
            long assignmentId,
            long ticketId
    ) throws IOException {
        if (assignmentId <= 0 || ticketId <= 0) {
            throw new IllegalArgumentException(
                    "Los identificadores de la asignación y del turno deben ser positivos."
            );
        }

        String[] response = request(
                command + ";" + assignmentId + ";" + ticketId
        );

        if (response.length != 4
                || !confirmationType.equals(response[1])) {
            throw new IOException(
                    "La confirmación del cambio de estado no es válida."
            );
        }

        long receivedAssignmentId = positiveLong(
                response[2], "asignación"
        );
        long receivedTicketId = positiveLong(
                response[3], "turno"
        );

        if (receivedAssignmentId != assignmentId
                || receivedTicketId != ticketId) {
            throw new IOException(
                    "La confirmación no corresponde al turno y asignación solicitados."
            );
        }
    }

    // Estado operativo de la ventanilla de la asignación (ACTIVA, PAUSADA o
    // INACTIVA). Las tres solicitudes responden OK;WINDOW_STATE;ventanilla;estado.
    public String getWindowState(long assignmentId) throws IOException {
        return windowState(request("GET_WINDOW_STATE;" + requireAssignment(assignmentId)));
    }

    public String pauseWindow(long assignmentId) throws IOException {
        return windowState(request("PAUSE_WINDOW;" + requireAssignment(assignmentId)));
    }

    public String resumeWindow(long assignmentId) throws IOException {
        return windowState(request("RESUME_WINDOW;" + requireAssignment(assignmentId)));
    }

    private static long requireAssignment(long assignmentId) {
        if (assignmentId <= 0) {
            throw new IllegalArgumentException("Seleccione una asignación válida.");
        }
        return assignmentId;
    }

    private String windowState(String[] response) throws IOException {
        if (response.length != 4 || !"WINDOW_STATE".equals(response[1])) {
            throw new IOException("La respuesta del estado de la ventanilla no es válida.");
        }
        positiveInt(response[2], "ventanilla");

        String state = response[3];
        if (!"ACTIVA".equals(state) && !"PAUSADA".equals(state) && !"INACTIVA".equals(state)) {
            throw new IOException("El estado de la ventanilla no es válido.");
        }
        return state;
    }

    private int positiveInt(String value, String field) throws IOException {
        long number = positiveLong(value, field);
        if (number > Integer.MAX_VALUE) {
            throw new IOException("El identificador de " + field + " supera el rango permitido.");
        }
        return (int) number;
    }

    private String[] request(String message) throws IOException {
        return transport.authenticated(session.token(), message);
    }
}
