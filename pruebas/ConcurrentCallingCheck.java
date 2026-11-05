import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

public class ConcurrentCallingCheck {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 5000;

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

    private static Assignment findAssignment(String[] response, int operator, int window) {
        for (int index = 3; index < response.length; index += 3) {
            if (Integer.parseInt(response[index + 1]) == operator
                    && Integer.parseInt(response[index + 2]) == window) {
                return new Assignment(Long.parseLong(response[index]), operator, window);
            }
        }
        throw new IllegalStateException("No aparece la asignación activa del operador " + operator
                + " y ventanilla " + window + ".");
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

    private static boolean matches(String raw, String type, Assignment assignment, String code) {
        String[] parts = fields(raw);
        if ("CALLED".equals(type)) {
            return parts.length == 6 && "OK".equals(parts[0]) && type.equals(parts[1])
                    && code.equals(parts[2]) && parts[4].equals(String.valueOf(assignment.operator()))
                    && parts[5].equals(String.valueOf(assignment.window()));
        }
        return parts.length == 9 && "OK".equals(parts[0]) && "ACTIVE_TICKET".equals(parts[1])
                && code.equals(parts[3]) && parts[5].equals(String.valueOf(assignment.id()))
                && parts[6].equals(String.valueOf(assignment.operator()))
                && parts[7].equals(String.valueOf(assignment.window())) && "LLAMADO".equals(parts[8]);
    }

    public static void main(String[] args) {
        try {
            System.out.println("PRUEBA DE DOS LLAMADOS COORDINADOS - " + LocalDateTime.now());
            String[] assignments = fields(request("LIST_ASSIGNMENTS"));
            if (assignments.length < 3 || !"OK".equals(assignments[0])
                    || !"ASSIGNMENTS".equals(assignments[1])
                    || assignments.length != 3 + 3 * Integer.parseInt(assignments[2])) {
                throw new IllegalStateException("No se recibió una lista válida de asignaciones.");
            }
            Assignment first = findAssignment(assignments, 1, 1);
            Assignment second = findAssignment(assignments, 2, 2);
            for (Assignment assignment : List.of(first, second)) {
                String active = request("GET_ACTIVE_TICKET;" + assignment.id());
                System.out.println("Antes, asignación " + assignment.id() + ": " + active);
                if (!"OK;NO_ACTIVE_TICKET".equals(active)) {
                    throw new IllegalStateException("La ventanilla " + assignment.window() + " debe estar libre.");
                }
            }
            String[] snapshot = fields(request("GET_DISPLAY_SNAPSHOT"));
            if (snapshot.length < 4 || !"OK".equals(snapshot[0]) || !"DISPLAY_SNAPSHOT".equals(snapshot[1])) {
                throw new IllegalStateException("No se recibió una captura válida.");
            }
            int waiting = Integer.parseInt(snapshot[2]);
            int recent = Integer.parseInt(snapshot[3]);
            if (waiting < 2 || snapshot.length != 4 + 7 * (waiting + recent)) {
                throw new IllegalStateException("Se necesitan al menos dos turnos en espera.");
            }
            Set<String> expected = new HashSet<>();
            for (int offset : new int[]{4, 11}) {
                if (!"true".equals(snapshot[offset + 3]) || !"EN_ESPERA".equals(snapshot[offset + 4])
                        || !"Información general".equals(snapshot[offset + 2])) {
                    throw new IllegalStateException("Los primeros dos turnos deben ser preferenciales de Información general.");
                }
                expected.add(snapshot[offset + 1]);
            }
            if (expected.size() != 2) throw new IllegalStateException("Se recibieron códigos repetidos.");
            System.out.println("Turnos esperados: " + expected);
            Result firstResult;
            Result secondResult;
            try (Connection firstConnection = new Connection();
                 Connection secondConnection = new Connection();
                 ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor()) {
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);
                Future<Result> firstTask = workers.submit(() -> call(firstConnection, first, ready, start));
                Future<Result> secondTask = workers.submit(() -> call(secondConnection, second, ready, start));
                if (!ready.await(2, TimeUnit.SECONDS)) throw new IllegalStateException("No se prepararon ambos clientes.");
                start.countDown();
                firstResult = firstTask.get();
                secondResult = secondTask.get();
            }
            List<Assignment> targets = List.of(first, second);
            List<Result> results = List.of(firstResult, secondResult);
            Set<String> received = new HashSet<>();
            boolean passed = true;
            for (int index = 0; index < 2; index++) {
                Assignment assignment = targets.get(index);
                Result result = results.get(index);
                System.out.println("Respuesta ventanilla " + assignment.window() + ": "
                        + (result.error() == null ? result.response() : result.error())
                        + " | duración " + result.durationMs() + " ms");
                String active = request("GET_ACTIVE_TICKET;" + assignment.id());
                System.out.println("Después, asignación " + assignment.id() + ": " + active);
                if (result.error() != null) { passed = false; continue; }
                String[] parts = fields(result.response());
                if (parts.length != 6 || !"CALLED".equals(parts[1])) { passed = false; continue; }
                String code = parts[2];
                passed &= received.add(code) && matches(result.response(), "CALLED", assignment, code)
                        && matches(active, "ACTIVE_TICKET", assignment, code);
            }
            double differenceMs = Math.abs(firstResult.sentAt() - secondResult.sentAt()) / 1_000_000.0;
            System.out.printf(Locale.ROOT, "Separación entre inicios de envío: %.3f ms%n", differenceMs);
            passed &= received.equals(expected);
            System.out.println(passed ? "RESULTADO: DOS TURNOS DISTINTOS, ASIGNACIONES CORRECTAS."
                    : "RESULTADO: REVISAR RESPUESTAS Y CONSOLA DE CORESERVER.");
            System.out.println("Guardar esta salida y comprobar las filas en MySQL. No se repitió ningún llamado automáticamente.");
        } catch (Exception exception) {
            System.err.println("PRUEBA DETENIDA: " + exception);
            System.err.println("Consultar las asignaciones y revisar CoreServer antes de repetir la prueba.");
        }
    }
}
