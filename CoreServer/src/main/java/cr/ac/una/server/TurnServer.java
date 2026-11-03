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

    public TurnServer(int port, TurnRequestHandler requestHandler) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("El puerto no es válido.");
        }

        this.port = port;
        this.requestHandler = Objects.requireNonNull(
                requestHandler,
                "El manejador de solicitudes es obligatorio."
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

            String response = requestHandler.handle(request);

            output.write(response);
            output.newLine();
            output.flush();

        } catch (IOException exception) {
            System.err.println(
                    "No se pudo atender la conexión: "
                    + exception.getMessage()
            );
        }
    }
}
