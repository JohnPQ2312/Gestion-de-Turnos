package cr.ac.una.operatorsystem.client;

import cr.ac.una.operatorsystem.model.ReportResult;
import cr.ac.una.operatorsystem.model.ReportType;
import cr.ac.una.operatorsystem.model.UserSession;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Solicita a CoreServer las cinco consultas de la sección 13 y convierte sus
 * respuestas en tablas legibles. No accede a MySQL.
 *
 * Usa el mismo protocolo que OperatorServerClient: una conexión por
 * solicitud, una línea UTF-8 y campos separados por punto y coma con los
 * textos codificados.
 *
 * Cada solicitud va con la sesión (AUTH;token;comando). CoreServer solo
 * atiende las consultas a un perfil ADMIN.
 */
public class ReportServerClient {

    private static final DateTimeFormatter DATE_TIME
            = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final String host;
    private final int port;
    private final UserSession session;

    public ReportServerClient(String host, int port, UserSession session) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("El servidor es obligatorio.");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("El puerto no es válido.");
        }
        this.host = host;
        this.port = port;
        this.session = Objects.requireNonNull(session, "La sesión es obligatoria.");
    }

    public ReportResult fetch(ReportType type, LocalDate from, LocalDate to) throws IOException {
        Objects.requireNonNull(type, "La consulta es obligatoria.");
        Objects.requireNonNull(from, "La fecha inicial es obligatoria.");
        Objects.requireNonNull(to, "La fecha final es obligatoria.");

        if (from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "La fecha inicial no puede ser posterior a la fecha final."
            );
        }

        String[] response = request(type.command() + ";" + from + ";" + to);

        if (response.length < 3 || !type.command().equals(response[1])) {
            throw new IOException("La respuesta no corresponde a la consulta solicitada.");
        }

        int count = nonNegativeInt(response[2], "cantidad de filas");

        return switch (type) {
            case ATTENDED ->
                attended(response, count);
            case BY_OPERATOR ->
                byOperator(response, count);
            case BY_SERVICE ->
                byService(response, count);
            case WAIT_TIMES ->
                durations(response, count, "Generación", "Inicio de atención", "Espera (h:mm:ss)");
            case ATTENTION_TIMES ->
                durations(response, count, "Inicio de atención", "Fin de atención", "Atención (h:mm:ss)");
        };
    }

    // código;servicio;prioridad;ventanilla;id funcionario;funcionario;generación;inicio;fin
    private ReportResult attended(String[] response, int count) throws IOException {
        int width = 9;
        requireLength(response, 3, count, width);

        List<List<String>> rows = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            int start = 3 + index * width;
            rows.add(List.of(
                    response[start],
                    response[start + 1],
                    "true".equals(response[start + 2]) ? "Preferencial" : "Regular",
                    response[start + 3],
                    response[start + 5],
                    dateTime(response[start + 6]),
                    dateTime(response[start + 7]),
                    dateTime(response[start + 8])
            ));
        }

        return new ReportResult(
                List.of("Turno", "Servicio", "Prioridad", "Ventanilla", "Funcionario",
                        "Generación", "Inicio de atención", "Fin de atención"),
                rows,
                count + (count == 1 ? " turno atendido." : " turnos atendidos.")
        );
    }

    // id;nombre;cantidad
    private ReportResult byOperator(String[] response, int count) throws IOException {
        int width = 3;
        requireLength(response, 3, count, width);

        List<List<String>> rows = new ArrayList<>();
        long total = 0;
        for (int index = 0; index < count; index++) {
            int start = 3 + index * width;
            long attended = nonNegativeLong(response[start + 2], "cantidad atendida");
            total += attended;
            rows.add(List.of(response[start], response[start + 1], String.valueOf(attended)));
        }

        return new ReportResult(
                List.of("Id", "Funcionario", "Personas atendidas"),
                rows,
                "Total de personas atendidas: " + total + "."
        );
    }

    // código;nombre;emitidos
    private ReportResult byService(String[] response, int count) throws IOException {
        int width = 3;
        requireLength(response, 3, count, width);

        List<List<String>> rows = new ArrayList<>();
        long total = 0;
        for (int index = 0; index < count; index++) {
            int start = 3 + index * width;
            long issued = nonNegativeLong(response[start + 2], "cantidad emitida");
            total += issued;
            rows.add(List.of(response[start], response[start + 1], String.valueOf(issued)));
        }

        return new ReportResult(
                List.of("Código", "Servicio", "Turnos emitidos"),
                rows,
                "Total de turnos emitidos: " + total + "."
        );
    }

    // n;promedio;mínimo;máximo; y por turno: código;servicio;desde;hasta;segundos
    private ReportResult durations(
            String[] response, int count, String fromLabel, String toLabel, String durationLabel
    ) throws IOException {
        int width = 5;
        requireLength(response, 6, count, width);

        long average = nonNegativeLong(response[3], "promedio");
        long minimum = nonNegativeLong(response[4], "mínimo");
        long maximum = nonNegativeLong(response[5], "máximo");

        List<List<String>> rows = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            int start = 6 + index * width;
            rows.add(List.of(
                    response[start],
                    response[start + 1],
                    dateTime(response[start + 2]),
                    dateTime(response[start + 3]),
                    duration(nonNegativeLong(response[start + 4], "duración"))
            ));
        }

        String summary = count == 0
                ? "Sin turnos en el rango."
                : count + (count == 1 ? " turno. " : " turnos. ")
                + "Promedio " + duration(average)
                + " · mínimo " + duration(minimum)
                + " · máximo " + duration(maximum) + " (h:mm:ss).";

        return new ReportResult(
                List.of("Turno", "Servicio", fromLabel, toLabel, durationLabel),
                rows,
                summary
        );
    }

    private void requireLength(String[] response, int header, int count, int width)
            throws IOException {
        if (response.length != header + count * width) {
            throw new IOException("La respuesta no tiene la cantidad de campos esperada.");
        }
    }

    private static String duration(long seconds) {
        return String.format("%d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    private static String dateTime(String value) throws IOException {
        try {
            return LocalDateTime.parse(value).format(DATE_TIME);
        } catch (DateTimeParseException exception) {
            throw new IOException("La respuesta contiene una fecha inválida.", exception);
        }
    }

    private static int nonNegativeInt(String value, String field) throws IOException {
        long number = nonNegativeLong(value, field);
        if (number > Integer.MAX_VALUE) {
            throw new IOException("El valor de " + field + " supera el rango permitido.");
        }
        return (int) number;
    }

    private static long nonNegativeLong(String value, String field) throws IOException {
        try {
            long number = Long.parseLong(value);
            if (number < 0) {
                throw new IOException("El valor de " + field + " no puede ser negativo.");
            }
            return number;
        } catch (NumberFormatException exception) {
            throw new IOException("El valor de " + field + " no es un número válido.", exception);
        }
    }

    private String[] request(String message) throws IOException {
        String line;

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3000);
            socket.setSoTimeout(15000);

            try (
                    BufferedWriter output = new BufferedWriter(
                            new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
                    ); BufferedReader input = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
                    )) {
                output.write("AUTH;" + session.token() + ";" + message);
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
        // Igual que OperatorServerClient: un ERROR del servidor es un rechazo
        // con mensaje para el usuario, no una falla de comunicación.
        if (response.length == 2 && "ERROR".equals(response[0])) {
            throw new IllegalArgumentException(response[1]);
        }
        if (response.length < 2 || !"OK".equals(response[0])) {
            throw new IOException("El formato de respuesta no es válido.");
        }

        return response;
    }
}
