package cr.ac.una.costumerturn.client;

import cr.ac.una.costumerturn.model.ServiceOption;
import cr.ac.una.costumerturn.model.TicketConfirmation;
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
import java.util.ArrayList;
import java.util.List;

public class TurnServerClient {

    private final String host;
    private final int port;

    public TurnServerClient(String host, int port) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("La dirección del servidor es obligatoria.");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("El puerto no es válido.");
        }
        this.host = host;
        this.port = port;
    }

    public List<ServiceOption> listServices() throws IOException {
        String[] response = request("LIST_SERVICES");

        if (response.length < 3 || !"SERVICES".equals(response[1])) {
            throw new IOException("La respuesta del catálogo no es válida.");
        }

        int count;
        try {
            count = Integer.parseInt(response[2]);
        } catch (NumberFormatException exception) {
            throw new IOException("La cantidad de servicios no es válida.", exception);
        }

        if (count < 0 || (response.length - 3) % 2 != 0
                || count != (response.length - 3) / 2) {
            throw new IOException("El catálogo recibido está incompleto.");
        }

        List<ServiceOption> services = new ArrayList<>();
        for (int index = 3; index < response.length; index += 2) {
            if (response[index].isBlank() || response[index + 1].isBlank()) {
                throw new IOException("Se recibió un servicio sin código o nombre.");
            }
            services.add(new ServiceOption(response[index], response[index + 1]));
        }
        return List.copyOf(services);
    }

    public TicketConfirmation generateTicket(String serviceCode, boolean priority)
            throws IOException {
        if (serviceCode == null || serviceCode.isBlank()) {
            throw new IllegalArgumentException("Debe seleccionar un servicio.");
        }

        String[] response = request(
                "GENERATE_TICKET;"
                + URLEncoder.encode(serviceCode, StandardCharsets.UTF_8)
                + ";" + priority
        );

        if (response.length != 5 || !"TICKET".equals(response[1])
                || response[2].isBlank() || response[3].isBlank()
                || !Boolean.toString(priority).equals(response[4])) {
            throw new IOException("La confirmación del turno no es válida.");
        }

        return new TicketConfirmation(response[2], response[3], priority);
    }

    private String[] request(String message) throws IOException {
        String line;

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3000);
            socket.setSoTimeout(5000);

            try (
                    BufferedWriter output = new BufferedWriter(
                            new OutputStreamWriter(
                                    socket.getOutputStream(), StandardCharsets.UTF_8
                            )
                    ); BufferedReader input = new BufferedReader(
                            new InputStreamReader(
                                    socket.getInputStream(), StandardCharsets.UTF_8
                            )
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
                response[index] = URLDecoder.decode(
                        response[index], StandardCharsets.UTF_8
                );
            }
        } catch (IllegalArgumentException exception) {
            throw new IOException("La respuesta tiene una codificación inválida.", exception);
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
