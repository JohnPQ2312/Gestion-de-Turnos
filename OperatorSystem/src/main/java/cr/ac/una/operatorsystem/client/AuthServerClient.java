package cr.ac.una.operatorsystem.client;

import cr.ac.una.operatorsystem.network.NetworkSettings;
import cr.ac.una.operatorsystem.model.UserSession;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

// Inicia y cierra sesiones por sockets; las credenciales y el token no se guardan en archivos.
public class AuthServerClient {

    // Tiempos de las solicitudes normales, iguales a los demás clientes.
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 15000;

    private final String host;
    private final int port;

    public AuthServerClient(String host, int port) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("La dirección del servidor es obligatoria.");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("El puerto no es válido.");
        }
        this.host = host;
        this.port = port;
    }

    // LOGIN;usuario;contraseña -> OK;LOGIN;token;id;nombre;usuario;rol
    public UserSession login(String username, String password) throws IOException {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Ingrese el usuario y la contraseña.");
        }

        String[] response = ServerProtocol.request(host, port, "LOGIN;"
                + URLEncoder.encode(username.trim(), StandardCharsets.UTF_8) + ";"
                + URLEncoder.encode(password, StandardCharsets.UTF_8),
                CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);

        if (response.length != 7 || !"LOGIN".equals(response[1])) {
            throw new IOException("La respuesta de inicio de sesión no es válida.");
        }

        int operatorId;
        try {
            operatorId = Integer.parseInt(response[3]);
        } catch (NumberFormatException exception) {
            throw new IOException("La respuesta de inicio de sesión no es válida.", exception);
        }

        return new UserSession(response[2], operatorId, response[4], response[5], response[6]);
    }

    // Cierra la sesión en el servidor. Si ya había vencido, no es un error.
    public void logout(UserSession session) throws IOException {
        logout(session, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
    }

    // Igual que logout, con tiempos propios. Al cerrar la aplicación se usa un
    // plazo corto para no retener el proceso si el servidor no responde.
    public void logout(UserSession session, int connectTimeoutMs, int readTimeoutMs)
            throws IOException {
        Objects.requireNonNull(session, "La sesión es obligatoria.");
        try {
            ServerProtocol.request(host, port, "AUTH;" + session.token() + ";LOGOUT",
                    connectTimeoutMs, readTimeoutMs);
        } catch (AccessDeniedException ignored) {
            // La sesión ya no existía en el servidor.
        }
    }

    // Envío y lectura de una línea del protocolo, con el mismo formato que
    // los demás clientes de OperatorSystem.
    static final class ServerProtocol {

        private ServerProtocol() {
        }

        static String[] request(
                String host, int port, String message, int connectTimeoutMs, int readTimeoutMs
        ) throws IOException {
            String line;

            try (Socket socket = NetworkSettings.open(host, port, connectTimeoutMs, readTimeoutMs)) {

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

            if (response.length == 3 && "DENIED".equals(response[0])) {
                throw new AccessDeniedException(response[1], response[2]);
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
}
