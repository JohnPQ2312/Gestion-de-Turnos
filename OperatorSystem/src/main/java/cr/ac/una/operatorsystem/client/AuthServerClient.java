package cr.ac.una.operatorsystem.client;

import cr.ac.una.operatorsystem.model.UserSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

// Inicia y cierra sesiones por sockets; las credenciales y el token no se guardan en archivos.
public class AuthServerClient {

    private final ProtocolTransport transport;

    public AuthServerClient(String host, int port) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("La dirección del servidor es obligatoria.");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("El puerto no es válido.");
        }
        this.transport = new ProtocolTransport(host, port, ProtocolTransport.Messages.GENERAL);
    }

    // LOGIN;usuario;contraseña -> OK;LOGIN;token;id;nombre;usuario;rol
    public UserSession login(String username, String password) throws IOException {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Ingrese el usuario y la contraseña.");
        }

        // El usuario se recorta; la contraseña se envía tal como se escribió.
        String[] response = transport.send("LOGIN;"
                + URLEncoder.encode(username.trim(), StandardCharsets.UTF_8) + ";"
                + URLEncoder.encode(password, StandardCharsets.UTF_8));

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
        logout(session, ProtocolTransport.CONNECT_TIMEOUT_MS, ProtocolTransport.READ_TIMEOUT_MS);
    }

    // Igual que logout, con tiempos propios. Al cerrar la aplicación se usa un
    // plazo corto para no retener el proceso si el servidor no responde.
    public void logout(UserSession session, int connectTimeoutMs, int readTimeoutMs)
            throws IOException {
        Objects.requireNonNull(session, "La sesión es obligatoria.");
        try {
            transport.authenticated(session.token(), "LOGOUT", connectTimeoutMs, readTimeoutMs);
        } catch (AccessDeniedException ignored) {
            // La sesión ya no existía en el servidor.
        }
    }
}
