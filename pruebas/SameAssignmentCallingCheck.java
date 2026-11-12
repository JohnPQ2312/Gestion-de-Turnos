import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * Envía dos CALL_NEXT simultáneos sobre UNA MISMA asignación.
 *
 * Resultado esperado: un único llamado exitoso y un rechazo por ocupación.
 * Después comprueba el estado guardado: un solo turno activo en la asignación
 * y los demás turnos que estaban en espera siguen en espera.
 *
 * No reintenta ninguna escritura. Uso:
 *   java pruebas/SameAssignmentCallingCheck.java [operador] [ventanilla]
 * Por defecto utiliza el operador 1 en la ventanilla 1.
 */
public class SameAssignmentCallingCheck {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 5000;
    private static final int SNAPSHOT_WAITING_LIMIT = 50;
    private static final int TICKET_FIELDS = 7;
    private static final String OCCUPIED_MESSAGE
            = "La asignación, el operador o la ventanilla ya tienen un turno activo.";

    private record Assignment(long id, int operator, int window) {}
    private record Result(String response, String error, long sentAt, long durationMs) {}
    private record SnapshotTicket(long ticketId, String code, String service, boolean priority,
            String state, String window) {}
    private record Snapshot(List<SnapshotTicket> waiting, List<SnapshotTicket> recent) {}

    private static final class Connection implements AutoCloseable {
        private final Socket socket = new Socket();
        private final BufferedReader input;
        private final BufferedWriter output;

        Connection() throws IOException {
            try {
                socket.connect(new InetSocketAddress(HOST, PORT), 3000);
                socket.setSoTimeout(30000);
                input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            } catch (IOException exception) {
                socket.close();
                throw exception;
            }
        }

        String request(String command) throws IOException {
            output.write(command);
            output.newLine();
            output.flush();
            String response = input.readLine();
            if (response == null) throw new IOException("El servidor cerró sin responder.");
            return response;
        }

        public void close() throws IOException {
            socket.close();
        }
    }

    private static String request(String command) throws IOException {
        try (Connection connection = new Connection()) {
            return connection.request(command);
        }
    }

    private static String[] fields(String response) {
        return Arrays.stream(response.split(";", -1))
                .map(value -> URLDecoder.decode(value, StandardCharsets.UTF_8))
                .toArray(String[]::new);
    }

    private static Assignment findAssignment(int operator, int window) throws IOException {
        String[] response = fields(request("LIST_ASSIGNMENTS"));
        if (response.length < 3 || !"OK".equals(response[0]) || !"ASSIGNMENTS".equals(response[1])
                || response.length != 3 + 3 * Integer.parseInt(response[2])) {
            throw new IllegalStateException("No se recibió una lista válida de asignaciones.");
        }
        for (int index = 3; index < response.length; index += 3) {
            if (Integer.parseInt(response[index + 1]) == operator
                    && Integer.parseInt(response[index + 2]) == window) {
                return new Assignment(Long.parseLong(response[index]), operator, window);
            }
        }
        throw new IllegalStateException("No aparece una asignación activa del operador " + operator
                + " en la ventanilla " + window + ".");
    }

    private static Snapshot snapshot() throws IOException {
        String[] parts = fields(request("GET_DISPLAY_SNAPSHOT"));
        if (parts.length < 4 || !"OK".equals(parts[0]) || !"DISPLAY_SNAPSHOT".equals(parts[1])) {
            throw new IllegalStateException("No se recibió una captura válida.");
        }
        int waiting = Integer.parseInt(parts[2]);
        int recent = Integer.parseInt(parts[3]);
        if (parts.length != 4 + TICKET_FIELDS * (waiting + recent)) {
            throw new IllegalStateException("La captura no tiene la cantidad de campos esperada.");
        }
        List<SnapshotTicket> tickets = new ArrayList<>();
        for (int offset = 4; offset < parts.length; offset += TICKET_FIELDS) {
            tickets.add(new SnapshotTicket(Long.parseLong(parts[offset]), parts[offset + 1],
                    parts[offset + 2], Boolean.parseBoolean(parts[offset + 3]),
                    parts[offset + 4], parts[offset + 5]));
        }
        return new Snapshot(List.copyOf(tickets.subList(0, waiting)),
                List.copyOf(tickets.subList(waiting, tickets.size())));
    }

    private static Result call(Connection connection, Assignment assignment,
            CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        long sentAt = 0;
        try {
            if (!start.await(2, TimeUnit.SECONDS)) {
                throw new IOException("No se liberó el inicio coordinado.");
            }
            sentAt = System.nanoTime();
            String response = connection.request("CALL_NEXT;" + assignment.id());
            return new Result(response, null, sentAt,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sentAt));
        } catch (Exception exception) {
            return new Result(null, exception.toString(), sentAt, 0);
        }
    }

    private static boolean isCalledFor(Result result, Assignment assignment) {
        if (result.error() != null) return false;
        String[] parts = fields(result.response());
        return parts.length == 6 && "OK".equals(parts[0]) && "CALLED".equals(parts[1])
                && parts[4].equals(String.valueOf(assignment.operator()))
                && parts[5].equals(String.valueOf(assignment.window()));
    }

    private static boolean isOccupiedRejection(Result result) {
        if (result.error() != null) return false;
        String[] parts = fields(result.response());
        return parts.length == 2 && "ERROR".equals(parts[0]) && OCCUPIED_MESSAGE.equals(parts[1]);
    }

    private static Set<String> codes(List<SnapshotTicket> tickets) {
        Set<String> codes = new LinkedHashSet<>();
        for (SnapshotTicket ticket : tickets) codes.add(ticket.code());
        return codes;
    }

    private static void check(List<String> failures, boolean condition, String description) {
        System.out.println((condition ? "  [OK]    " : "  [FALLA] ") + description);
        if (!condition) failures.add(description);
    }

    public static void main(String[] args) {
        int operator = args.length >= 1 ? Integer.parseInt(args[0]) : 1;
        int window = args.length >= 2 ? Integer.parseInt(args[1]) : 1;

        try {
            System.out.println("PRUEBA DE DOS LLAMADOS SIMULTÁNEOS SOBRE UNA MISMA ASIGNACIÓN - "
                    + LocalDateTime.now());

            // 1. Preparación: asignación vigente y libre, al menos dos turnos en espera.
            Assignment assignment = findAssignment(operator, window);
            System.out.println("Asignación " + assignment.id() + ": operador " + operator
                    + ", ventanilla " + window);

            String activeBefore = request("GET_ACTIVE_TICKET;" + assignment.id());
            System.out.println("Antes: " + activeBefore);
            if (!"OK;NO_ACTIVE_TICKET".equals(activeBefore)) {
                throw new IllegalStateException("La asignación debe estar libre antes de la prueba.");
            }

            Snapshot before = snapshot();
            if (before.waiting().size() >= SNAPSHOT_WAITING_LIMIT) {
                throw new IllegalStateException("Hay " + SNAPSHOT_WAITING_LIMIT
                        + " o más turnos en espera; la captura podría estar recortada y no se podría "
                        + "verificar el estado posterior de todos.");
            }
            if (before.waiting().size() < 2) {
                throw new IllegalStateException("Se necesitan al menos dos turnos en espera.");
            }
            Set<String> waitingBefore = codes(before.waiting());
            System.out.println("Turnos en espera antes (" + waitingBefore.size() + "): " + waitingBefore);
            System.out.println("La compatibilidad de esos turnos con la asignación se comprueba en SQL "
                    + "(ver pruebas/README.md); el protocolo no la expone.");

            // 2. Envío coordinado de dos CALL_NEXT sobre la misma asignación, sin reintentos.
            Result firstResult;
            Result secondResult;
            try (Connection firstConnection = new Connection();
                 Connection secondConnection = new Connection();
                 ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor()) {
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);
                Future<Result> firstTask = workers.submit(() -> call(firstConnection, assignment, ready, start));
                Future<Result> secondTask = workers.submit(() -> call(secondConnection, assignment, ready, start));
                if (!ready.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("No se prepararon ambos clientes.");
                }
                start.countDown();
                firstResult = firstTask.get();
                secondResult = secondTask.get();
            }

            List<Result> results = List.of(firstResult, secondResult);
            for (int index = 0; index < results.size(); index++) {
                Result result = results.get(index);
                System.out.println("Respuesta " + (index + 1) + ": "
                        + (result.error() == null ? result.response() : result.error())
                        + " | " + result.durationMs() + " ms");
            }
            System.out.printf(Locale.ROOT, "Separación entre inicios de envío: %.3f ms%n",
                    Math.abs(firstResult.sentAt() - secondResult.sentAt()) / 1_000_000.0);

            // 3. Verificación de respuestas y del estado guardado.
            List<String> failures = new ArrayList<>();
            System.out.println("Comprobaciones:");

            long called = results.stream().filter(r -> isCalledFor(r, assignment)).count();
            long rejected = results.stream().filter(SameAssignmentCallingCheck::isOccupiedRejection).count();
            check(failures, called == 1, "Exactamente un llamado exitoso para esta asignación (obtenidos: "
                    + called + ")");
            check(failures, rejected == 1, "Exactamente un rechazo por ocupación (obtenidos: " + rejected + ")");

            String calledCode = results.stream().filter(r -> isCalledFor(r, assignment))
                    .map(r -> fields(r.response())[2]).findFirst().orElse(null);
            check(failures, calledCode != null && waitingBefore.contains(calledCode),
                    "El turno llamado (" + calledCode + ") estaba en espera antes de la prueba");

            String[] active = fields(request("GET_ACTIVE_TICKET;" + assignment.id()));
            System.out.println("Después: " + String.join(";", active));
            check(failures, active.length == 9 && "ACTIVE_TICKET".equals(active[1])
                    && Objects.equals(calledCode, active[3])
                    && active[5].equals(String.valueOf(assignment.id()))
                    && "LLAMADO".equals(active[8]),
                    "GET_ACTIVE_TICKET devuelve el turno llamado, en estado LLAMADO, en esta asignación");

            Snapshot after = snapshot();
            Set<String> waitingAfter = codes(after.waiting());
            Set<String> expectedWaiting = new LinkedHashSet<>(waitingBefore);
            expectedWaiting.remove(calledCode);
            check(failures, !waitingAfter.contains(calledCode),
                    "El turno llamado ya no aparece en espera");
            check(failures, waitingAfter.containsAll(expectedWaiting),
                    "Los demás turnos siguen en espera: " + expectedWaiting);
            check(failures, waitingAfter.size() == waitingBefore.size() - 1,
                    "La espera bajó exactamente en uno (" + waitingBefore.size() + " -> "
                    + waitingAfter.size() + ")");

            System.out.println(failures.isEmpty()
                    ? "RESULTADO: UN SOLO LLAMADO Y UN RECHAZO POR OCUPACIÓN. ESTADO CONSISTENTE."
                    : "RESULTADO: REVISAR. Fallaron " + failures.size()
                    + " comprobaciones. Revisar la consola de CoreServer y MySQL.");

            // 4. Consultas para confirmar en phpMyAdmin.
            System.out.println();
            System.out.println("Confirmar en MySQL (base de la prueba):");
            System.out.println("SELECT ticket_id, visual_code, ticket_state, assignment_id, called_at FROM ticket "
                    + "WHERE visual_code IN (" + quoted(waitingBefore) + ") ORDER BY created_at, ticket_id;");
            System.out.println("SELECT assignment_id, COUNT(*) AS turnos_activos FROM ticket "
                    + "WHERE ticket_state IN ('LLAMADO', 'EN_ATENCION') AND assignment_id = " + assignment.id()
                    + " GROUP BY assignment_id;");
            System.out.println("Guardar esta salida. No se repitió ningún llamado automáticamente; "
                    + "el turno llamado queda LLAMADO para inspeccionarlo.");

        } catch (Exception exception) {
            System.err.println("PRUEBA DETENIDA: " + exception);
            System.err.println("Consultar la asignación y revisar CoreServer antes de repetir la prueba.");
        }
    }

    private static String quoted(Set<String> codes) {
        StringJoiner joiner = new StringJoiner(", ");
        for (String code : codes) joiner.add("'" + code.replace("'", "''") + "'");
        return joiner.toString();
    }
}
