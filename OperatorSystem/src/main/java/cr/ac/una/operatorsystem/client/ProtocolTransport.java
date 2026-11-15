package cr.ac.una.operatorsystem.client;

import cr.ac.una.operatorsystem.network.NetworkSettings;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

// Transporte de una línea del protocolo, común a los clientes de OperatorSystem.
// Cada solicitud abre su propio socket con NetworkSettings.open (TLS o loopback según la configuración),
// envía una línea UTF-8, lee una sola respuesta y cierra. Nunca reintenta: si la respuesta se pierde, la
// excepción llega al llamador, que decide cómo recuperar sin repetir una escritura.
// Clasifica DENIED (AccessDeniedException), ERROR (IllegalArgumentException con el mensaje del servidor)
// y cualquier otra respuesta que no sea OK (IOException). Cada cliente conserva sus mensajes y su parser.
final class ProtocolTransport {

    // Plazos de las solicitudes normales de todos los clientes.
    static final int CONNECT_TIMEOUT_MS = 3000;
    static final int READ_TIMEOUT_MS = 15000;

    // Textos de IOException propios de cada cliente: sin respuesta, codificación inválida y formato inválido.
    record Messages(String noResponse, String badEncoding, String badFormat) {

        // Atención, consultas e inicio de sesión.
        static final Messages GENERAL = new Messages(
                "El servidor no envió una respuesta.",
                "La respuesta tiene una codificación inválida.",
                "El formato de respuesta no es válido."
        );

        Messages {
            Objects.requireNonNull(noResponse);
            Objects.requireNonNull(badEncoding);
            Objects.requireNonNull(badFormat);
        }
    }

    private final String host;
    private final int port;
    private final Messages messages;

    // Cada cliente valida host y puerto con sus propios mensajes antes de crear el transporte.
    ProtocolTransport(String host, int port, Messages messages) {
        this.host = host;
        this.port = port;
        this.messages = Objects.requireNonNull(messages, "Los mensajes del transporte son obligatorios.");
    }

    // AUTH;token;comando con los plazos normales.
    String[] authenticated(String token, String command) throws IOException {
        return send("AUTH;" + token + ";" + command);
    }

    // AUTH;token;comando con plazos propios (por ejemplo, LOGOUT al salir).
    String[] authenticated(String token, String command, int connectTimeoutMs, int readTimeoutMs)
            throws IOException {
        return send("AUTH;" + token + ";" + command, connectTimeoutMs, readTimeoutMs);
    }

    // Línea ya armada (por ejemplo, LOGIN) con los plazos normales.
    String[] send(String line) throws IOException {
        return send(line, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
    }

    // Una conexión, una línea enviada y una línea leída. Devuelve los campos decodificados de una respuesta OK.
    String[] send(String line, int connectTimeoutMs, int readTimeoutMs) throws IOException {
        String received;

        try (Socket socket = NetworkSettings.open(host, port, connectTimeoutMs, readTimeoutMs)) {
            try (
                    BufferedWriter output = new BufferedWriter(
                            new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
                    ); BufferedReader input = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
                    )) {
                output.write(line);
                output.newLine();
                output.flush();
                received = input.readLine();
            }
        }

        if (received == null || received.isBlank()) {
            throw new IOException(messages.noResponse());
        }

        String[] response = received.split(";", -1);
        try {
            for (int index = 0; index < response.length; index++) {
                response[index] = URLDecoder.decode(response[index], StandardCharsets.UTF_8);
            }
        } catch (IllegalArgumentException exception) {
            throw new IOException(messages.badEncoding(), exception);
        }

        if (response.length == 3 && "DENIED".equals(response[0])) {
            throw new AccessDeniedException(response[1], response[2]);
        }
        // Un ERROR es un rechazo con mensaje para el usuario, no una falla de comunicación.
        if (response.length == 2 && "ERROR".equals(response[0])) {
            throw new IllegalArgumentException(response[1]);
        }
        if (response.length < 2 || !"OK".equals(response[0])) {
            throw new IOException(messages.badFormat());
        }

        return response;
    }
}
