package cr.ac.una.server;

import java.io.BufferedReader;
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

public class TurnServer {

    private final int port;
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

    public void start() throws IOException {
        // Las primeras pruebas utilizarán clientes locales.
        try (
                ServerSocket serverSocket = new ServerSocket(
                        port, 50, InetAddress.getLoopbackAddress()
                ); ExecutorService clients
                = Executors.newVirtualThreadPerTaskExecutor()) {
            System.out.println("CoreServer escuchando en el puerto " + port);

            while (!serverSocket.isClosed()) {
                Socket client = serverSocket.accept();
                clients.submit(() -> handleClient(client));
            }
        }
    }

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

            sendLine(output, requestHandler.handle(request));

        } catch (IOException exception) {
            System.err.println(
                    "No se pudo atender la conexión: "
                    + exception.getMessage()
            );
        }
    }

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

    private void sendLine(BufferedWriter output, String message) throws IOException {
        output.write(message);
        output.newLine();
        output.flush();
    }
}
