import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Comparte el acceso de las herramientas de prueba mediante sesiones de funcionarios.
// Pide credenciales de la aplicación y no requiere credenciales MySQL.
final class TestAccess implements AutoCloseable {
    private final String host;
    private final int port;
    private final Map<Integer, String> tokens = new LinkedHashMap<>();
    private final Map<Long, String> assignmentTokens = new ConcurrentHashMap<>();
    private final Console console = System.console();
    private final BufferedReader input = new BufferedReader(
            new InputStreamReader(System.in, StandardCharsets.UTF_8));
    private boolean inputWarningShown;

    TestAccess(String host, int port) {
        if (!"127.0.0.1".equals(host) || port < 1 || port > 65535) {
            throw new IllegalArgumentException("Las pruebas requieren un puerto local válido.");
        }
        this.host = host;
        this.port = port;
    }

    String listAssignments(int operatorId) throws IOException {
        return exchange("AUTH;" + login(operatorId) + ";LIST_ASSIGNMENTS", 30000);
    }

    void bindAssignment(long assignmentId, int operatorId) {
        String token = tokens.get(operatorId);
        if (assignmentId <= 0 || token == null) {
            throw new IllegalStateException("Falta una sesión propia para la asignación.");
        }
        String previous = assignmentTokens.putIfAbsent(assignmentId, token);
        if (previous != null && !previous.equals(token)) {
            throw new IllegalStateException("La asignación aparece ligada a dos funcionarios.");
        }
    }

    String authorize(String command) {
        if ("GET_DISPLAY_SNAPSHOT".equals(command)) {
            return command;
        }
        String[] parts = command.split(";", -1);
        if (parts.length != 2 || !("CALL_NEXT".equals(parts[0])
                || "GET_ACTIVE_TICKET".equals(parts[0]))) {
            throw new IllegalArgumentException("Comando de prueba no reconocido.");
        }
        String token = assignmentTokens.get(Long.parseLong(parts[1]));
        if (token == null) {
            throw new IllegalStateException("La asignación no tiene una sesión verificada.");
        }
        return "AUTH;" + token + ";" + command;
    }

    private String login(int operatorId) throws IOException {
        if (operatorId <= 0) {
            throw new IllegalArgumentException("El funcionario debe tener un identificador positivo.");
        }
        if (tokens.containsKey(operatorId)) {
            return tokens.get(operatorId);
        }
        System.out.println("Iniciar sesión del funcionario " + operatorId + ".");
        String username = readLine("Usuario: ").trim();
        char[] password = readPassword("Contraseña: ");
        String[] fields;
        try {
            if (username.isEmpty() || password.length == 0) {
                throw new IllegalArgumentException("Ingrese el usuario y la contraseña.");
            }
            String response = exchange("LOGIN;" + encode(username)
                    + ";" + encode(new String(password)), 30000);
            fields = Arrays.stream(response.split(";", -1))
                    .map(value -> URLDecoder.decode(value, StandardCharsets.UTF_8))
                    .toArray(String[]::new);
        } finally {
            Arrays.fill(password, '\0');
        }
        if (fields.length >= 3 && "DENIED".equals(fields[0])) {
            throw new IOException("Acceso rechazado: " + fields[fields.length - 1]);
        }
        if (fields.length != 7 || !"OK".equals(fields[0])
                || !"LOGIN".equals(fields[1]) || fields[2].isBlank()) {
            throw new IOException("El servidor no confirmó una sesión válida.");
        }
        // Conservar el token antes de validar la identidad permite cerrarlo
        // también cuando el usuario ingresó la cuenta de otro funcionario.
        int actualId = Integer.parseInt(fields[3]);
        String previous = tokens.putIfAbsent(actualId, fields[2]);
        if (previous != null) {
            logout(fields[2]);
        }
        if (actualId != operatorId) {
            throw new IllegalStateException("La cuenta ingresada corresponde al funcionario "
                    + actualId + ", pero esta prueba requiere el " + operatorId + ".");
        }
        return tokens.get(operatorId);
    }

    private String readLine(String prompt) throws IOException {
        String line;
        if (console != null) {
            line = console.readLine("%s", prompt);
        } else {
            System.out.print(prompt);
            System.out.flush();
            line = input.readLine();
        }
        if (line == null) {
            throw new IOException("La entrada terminó antes de completar el acceso.");
        }
        return line;
    }

    private char[] readPassword(String prompt) throws IOException {
        if (console != null) {
            char[] password = console.readPassword("%s", prompt);
            if (password == null) {
                throw new IOException("La entrada terminó antes de ingresar la contraseña.");
            }
            return password;
        }
        if (!inputWarningShown) {
            System.out.println("Aviso: esta consola muestra lo que escribe. Use una consola "
                    + "interactiva para ocultar la contraseña.");
            inputWarningShown = true;
        }
        return readLine(prompt).toCharArray();
    }

    private String exchange(String command, int timeoutMs) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), Math.min(timeoutMs, 3000));
            socket.setSoTimeout(timeoutMs);
            BufferedWriter output = new BufferedWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            output.write(command);
            output.newLine();
            output.flush();
            String response = reader.readLine();
            if (response == null) {
                throw new IOException("El servidor cerró sin confirmar la solicitud.");
            }
            return response;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void logout(String token) throws IOException {
        if (!"OK;LOGOUT".equals(exchange("AUTH;" + token + ";LOGOUT", 2000))) {
            throw new IOException("El servidor no confirmó el cierre de la sesión de prueba.");
        }
    }

    @Override
    public void close() {
        for (String token : tokens.values()) {
            try {
                logout(token);
            } catch (IOException exception) {
                System.err.println("No se confirmó el cierre de una sesión de prueba; "
                        + "vencerá en CoreServer. Detalle: " + exception.getMessage());
            }
        }
        assignmentTokens.clear();
        tokens.clear();
    }
}
