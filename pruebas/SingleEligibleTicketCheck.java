import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * Dos ventanillas distintas piden CALL_NEXT al mismo tiempo cuando existe
 * UN SOLO turno en espera, compatible con ambas.
 *
 * Resultado esperado: una ventanilla obtiene el turno y la otra recibe
 * OK;NO_TICKET. Ninguna recibe el mismo turno dos veces.
 *
 * No reintenta ninguna escritura. Uso:
 *   ver pruebas/README.md (Ejecutar.ps1 SingleEligibleTicketCheck)
 * Por defecto: operador 1 en ventanilla 1 y operador 2 en ventanilla 2.
 */
public class SingleEligibleTicketCheck {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = Integer.getInteger("turnos.pruebas.puerto", 5000);
    private static final TestAccess ACCESS = new TestAccess(HOST, PORT);
    private static final int TICKET_FIELDS = 7;

    private record Assignment(long id, int operator, int window) {}
    private record Result(String response, String error, long sentAt, long durationMs) {}

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
            output.write(ACCESS.authorize(command));
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
        String[] list = fields(ACCESS.listAssignments(operator));
        if (list.length < 3 || !"OK".equals(list[0]) || !"ASSIGNMENTS".equals(list[1])
                || list.length != 3 + 3 * Integer.parseInt(list[2])) {
            throw new IllegalStateException("No se recibió una lista válida de asignaciones propias.");
        }
        for (int index = 3; index < list.length; index += 3) {
            if (Integer.parseInt(list[index + 1]) == operator
                    && Integer.parseInt(list[index + 2]) == window) {
                Assignment assignment = new Assignment(Long.parseLong(list[index]), operator, window);
                ACCESS.bindAssignment(assignment.id(), operator);
                return assignment;
            }
        }
        throw new IllegalStateException("No aparece una asignación activa del operador " + operator
                + " en la ventanilla " + window + ".");
    }

    /** Devuelve los códigos de los turnos en espera según la pantalla pública. */
    private static List<String> waitingCodes() throws IOException {
        String[] parts = fields(request("GET_DISPLAY_SNAPSHOT"));
        if (parts.length < 4 || !"OK".equals(parts[0]) || !"DISPLAY_SNAPSHOT".equals(parts[1])) {
            throw new IllegalStateException("No se recibió una captura válida.");
        }
        int waiting = Integer.parseInt(parts[2]);
        int recent = Integer.parseInt(parts[3]);
        if (parts.length != 4 + TICKET_FIELDS * (waiting + recent)) {
            throw new IllegalStateException("La captura no tiene la cantidad de campos esperada.");
        }
        List<String> codes = new ArrayList<>();
        for (int index = 0; index < waiting; index++) {
            codes.add(parts[4 + index * TICKET_FIELDS + 1]);
        }
        return codes;
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

    private static boolean isCalled(Result result, Assignment assignment, String code) {
        if (result.error() != null) return false;
        String[] parts = fields(result.response());
        return parts.length == 6 && "OK".equals(parts[0]) && "CALLED".equals(parts[1])
                && code.equals(parts[2])
                && parts[4].equals(String.valueOf(assignment.operator()))
                && parts[5].equals(String.valueOf(assignment.window()));
    }

    private static boolean isNoTicket(Result result) {
        return result.error() == null && "OK;NO_TICKET".equals(result.response());
    }

    private static void check(List<String> failures, boolean condition, String description) {
        System.out.println((condition ? "  [OK]    " : "  [FALLA] ") + description);
        if (!condition) failures.add(description);
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            if (args.length > 4) throw new IllegalArgumentException("Se admiten op1, vent1, op2 y vent2.");
            int[] values = {1, 1, 2, 2};
            for (int index = 0; index < args.length; index++) {
                values[index] = Integer.parseInt(args[index]);
                if (values[index] <= 0) throw new IllegalArgumentException("Los identificadores deben ser positivos.");
            }
            System.out.println("PRUEBA DE UN ÚNICO TURNO ELEGIBLE PARA DOS VENTANILLAS - "
                    + LocalDateTime.now());

            // 1. Preparación: dos asignaciones distintas, libres, y un solo turno en espera.
            Assignment first = findAssignment(values[0], values[1]);
            Assignment second = findAssignment(values[2], values[3]);
            if (first.id() == second.id() || first.window() == second.window()
                    || first.operator() == second.operator()) {
                throw new IllegalStateException("Las dos asignaciones deben tener operador y ventanilla distintos.");
            }
            List<Assignment> assignments = List.of(first, second);

            for (Assignment assignment : assignments) {
                String active = request("GET_ACTIVE_TICKET;" + assignment.id());
                System.out.println("Antes, asignación " + assignment.id() + " (ventanilla "
                        + assignment.window() + "): " + active);
                if (!"OK;NO_ACTIVE_TICKET".equals(active)) {
                    throw new IllegalStateException("La ventanilla " + assignment.window()
                            + " debe estar libre antes de la prueba.");
                }
            }

            List<String> waitingBefore = waitingCodes();
            System.out.println("Turnos en espera antes: " + waitingBefore);
            if (waitingBefore.size() != 1) {
                throw new IllegalStateException("Debe haber exactamente un turno en espera; hay "
                        + waitingBefore.size() + ".");
            }
            String ticketCode = waitingBefore.get(0);
            System.out.println("Turno en disputa: " + ticketCode + ". Su compatibilidad con ambas "
                    + "asignaciones se comprueba en SQL (ver pruebas/README.md).");

            // 2. Envío coordinado desde las dos ventanillas, sin reintentos.
            Result firstResult;
            Result secondResult;
            try (Connection firstConnection = new Connection();
                 Connection secondConnection = new Connection();
                 ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor()) {
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);
                Future<Result> firstTask = workers.submit(() -> call(firstConnection, first, ready, start));
                Future<Result> secondTask = workers.submit(() -> call(secondConnection, second, ready, start));
                if (!ready.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("No se prepararon ambos clientes.");
                }
                start.countDown();
                firstResult = firstTask.get();
                secondResult = secondTask.get();
            }
            List<Result> results = List.of(firstResult, secondResult);
            for (int index = 0; index < 2; index++) {
                Result result = results.get(index);
                System.out.println("Respuesta ventanilla " + assignments.get(index).window() + ": "
                        + (result.error() == null ? result.response() : result.error())
                        + " | " + result.durationMs() + " ms");
            }
            System.out.printf(Locale.ROOT, "Separación entre inicios de envío: %.3f ms%n",
                    Math.abs(firstResult.sentAt() - secondResult.sentAt()) / 1_000_000.0);

            // 3. Verificación de respuestas y del estado guardado.
            List<String> failures = new ArrayList<>();
            System.out.println("Comprobaciones:");

            int winner = -1;
            int calledCount = 0;
            for (int index = 0; index < 2; index++) {
                if (isCalled(results.get(index), assignments.get(index), ticketCode)) {
                    calledCount++;
                    winner = index;
                }
            }
            check(failures, calledCount == 1, "Exactamente una ventanilla obtuvo " + ticketCode
                    + " (obtenidas: " + calledCount + ")");
            check(failures, winner >= 0 && isNoTicket(results.get(1 - winner)),
                    "La otra ventanilla recibió OK;NO_TICKET");

            for (int index = 0; index < 2; index++) {
                Assignment assignment = assignments.get(index);
                String[] active = fields(request("GET_ACTIVE_TICKET;" + assignment.id()));
                System.out.println("Después, asignación " + assignment.id() + ": " + String.join(";", active));
                if (index == winner) {
                    check(failures, active.length == 9 && "ACTIVE_TICKET".equals(active[1])
                            && ticketCode.equals(active[3])
                            && active[5].equals(String.valueOf(assignment.id()))
                            && "LLAMADO".equals(active[8]),
                            "Ventanilla " + assignment.window() + " tiene " + ticketCode + " en estado LLAMADO");
                } else {
                    check(failures, active.length == 2 && "NO_ACTIVE_TICKET".equals(active[1]),
                            "Ventanilla " + assignment.window() + " quedó sin turno activo");
                }
            }

            List<String> waitingAfter = waitingCodes();
            check(failures, waitingAfter.isEmpty(),
                    "No quedan turnos en espera (quedan: " + waitingAfter + ")");

            exitCode = failures.isEmpty() ? 0 : 1;
            System.out.println(failures.isEmpty()
                    ? "RESULTADO: UNA SOLA VENTANILLA OBTUVO EL TURNO Y LA OTRA NO RECIBIÓ NINGUNO. ESTADO CONSISTENTE."
                    : "RESULTADO: REVISAR. Fallaron " + failures.size()
                    + " comprobaciones. Revisar la consola de CoreServer y MySQL.");

            // 4. Consultas para confirmar en phpMyAdmin.
            System.out.println();
            System.out.println("Confirmar en MySQL (base de la prueba):");
            System.out.println("SELECT ticket_id, visual_code, ticket_state, assignment_id, called_at FROM ticket "
                    + "WHERE visual_code = '" + ticketCode.replace("'", "''") + "';");
            System.out.println("SELECT assignment_id, COUNT(*) AS turnos_activos FROM ticket "
                    + "WHERE ticket_state IN ('LLAMADO', 'EN_ATENCION') AND assignment_id IN ("
                    + first.id() + ", " + second.id() + ") GROUP BY assignment_id;");
            System.out.println("Guardar esta salida. No se repitió ningún llamado automáticamente; "
                    + "el turno llamado queda LLAMADO para inspeccionarlo.");

        } catch (Exception exception) {
            exitCode = 1;
            System.err.println("PRUEBA DETENIDA: " + exception);
            System.err.println("Consultar las asignaciones y revisar CoreServer antes de repetir la prueba.");
        } finally {
            ACCESS.close();
        }
        if (exitCode != 0) System.exit(exitCode);
    }
}
