package cr.ac.una.server;

import java.io.BufferedReader;
import cr.ac.una.config.NetworkSettings;
import cr.ac.una.config.ConnectionMetrics;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Acepta conexiones de clientes y atiende cada una en un hilo virtual.
// Las solicitudes normales usan una línea; las suscripciones públicas conservan el canal
// abierto.
public class TurnServer {

    private final int port;
    private NetworkSettings network;
    private final TurnRequestHandler requestHandler;
    private final DisplayEventHub displayEvents;

    public TurnServer(
            int port, TurnRequestHandler requestHandler, DisplayEventHub displayEvents
    ) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("El puerto no es válido.");
        }

        this.port = port;
        this.requestHandler = Objects.requireNonNull(
                requestHandler,
                "El manejador de solicitudes es obligatorio."
        );
        this.displayEvents = Objects.requireNonNull(
                displayEvents, "El centro de avisos de la pantalla es obligatorio."
        );
    }

    public TurnServer(NetworkSettings network, TurnRequestHandler handler, DisplayEventHub events) throws IOException {
        this(network.port(),handler,events); this.network=network;
    }

    // Acepta clientes y delega cada conexión al ejecutor de hilos virtuales.
    public void start() throws IOException {
        // El constructor anterior conserva loopback; la aplicación utiliza la configuración de red.
        try (
                ServerSocket serverSocket = network==null ? new ServerSocket(port,50,InetAddress.getLoopbackAddress()) : network.listen(); ExecutorService clients
                = Executors.newVirtualThreadPerTaskExecutor()) {
            System.out.println("CoreServer escuchando en el puerto " + port);

            while (!serverSocket.isClosed()) {
                Socket client = serverSocket.accept();
                clients.submit(() -> handleClient(client));
            }
        }
    }

    // Lee una solicitud; las suscripciones continúan escuchando y las demás reciben una
    // respuesta.
    private void handleClient(Socket client) {
        try (
                client;
                BufferedReader input = new BufferedReader(
                        new InputStreamReader(
                                client.getInputStream(),
                                StandardCharsets.UTF_8
                        )
                ); BufferedWriter output = new BufferedWriter(
                        new OutputStreamWriter(
                                client.getOutputStream(),
                                StandardCharsets.UTF_8
                        )
                )) {
            client.setSoTimeout(5000);

            String request = input.readLine();

            if (request == null) {
                return;
            }

            if ("SUBSCRIBE_DISPLAY".equals(request)) {
                streamDisplayChanges(output);
                return;
            }

            // Mide solo el procesamiento de requestHandler.handle(): no incluye
            // recibir la solicitud ni enviar la respuesta.
            ConnectionMetrics.reset();
            long start = System.nanoTime();
            String response = requestHandler.handle(request);
            long processingMillis = (System.nanoTime() - start) / 1_000_000;

            long openingMillis = ConnectionMetrics.openingMillis();

            System.out.println("[tiempo] " + commandName(request)
                    + " | procesamiento " + processingMillis + " ms"
                    + " | apertura de conexiones " + openingMillis + " ms"
                    + " (exitosas " + ConnectionMetrics.successfulOpenings()
                    + ", fallidas " + ConnectionMetrics.failedOpenings() + ")"
                    + " | resto: consultas, bloqueos y lógica "
                    + (processingMillis - openingMillis) + " ms"
                    + " | " + responseStatus(response));

            sendLine(output, response);
        } catch (IOException exception) {
            System.err.println(
                    "No se pudo atender la conexión: "
                    + exception.getMessage()
            );
        }
    }

    // Mantiene el canal de avisos y envía un latido cuando no hay cambios.
    private void streamDisplayChanges(BufferedWriter output) throws IOException {
        try (DisplayEventHub.Subscription subscription = displayEvents.subscribe()) {
            sendLine(output, "OK;SUBSCRIBED");

            while (!subscription.isClosed() && !Thread.currentThread().isInterrupted()) {
                boolean changed = subscription.awaitChange();
                if (!subscription.isClosed()) {
                    sendLine(output, changed ? "EVENT;DISPLAY_CHANGED" : "OK;HEARTBEAT");
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
    
    // Solo el nombre del comando: los parámetros pueden contener datos
    // sensibles. En AUTH;token;COMANDO;... se registra AUTH:COMANDO, nunca
    // el token; en LOGIN, nunca el usuario ni la contraseña.
    private static String commandName(String request) {
        if (request.startsWith("AUTH;")) {
            String[] parts = request.split(";", 4);
            return parts.length >= 3 ? "AUTH:" + parts[2] : "AUTH";
        }
        int separator = request.indexOf(';');
        return separator < 0 ? request : request.substring(0, separator);
    }

    private static String responseStatus(String response) {
        String[] parts = response.split(";", 3);
        return parts.length >= 2 && "OK".equals(parts[0])
                ? parts[0] + ";" + parts[1] : parts[0];
    }

    private void sendLine(BufferedWriter output, String message) throws IOException {
        output.write(message);
        output.newLine();
        output.flush();
    }
}
