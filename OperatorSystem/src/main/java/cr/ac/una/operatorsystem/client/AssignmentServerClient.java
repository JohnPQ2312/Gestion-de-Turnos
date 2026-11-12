package cr.ac.una.operatorsystem.client;

import cr.ac.una.operatorsystem.network.NetworkSettings;
import cr.ac.una.operatorsystem.model.AssignmentConfiguration;
import cr.ac.una.operatorsystem.model.AssignmentConfiguration.*;
import cr.ac.una.operatorsystem.model.UserSession;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

public class AssignmentServerClient implements AssignmentRequests {
    private final String host;
    private final int port;
    private final UserSession session;
    public AssignmentServerClient(String host, int port, UserSession session) {
        if (host == null || host.isBlank() || port < 1 || port > 65535) throw new IllegalArgumentException("Servidor o puerto no válido.");
        this.host = host; this.port = port; this.session = Objects.requireNonNull(session);
    }
    @Override
    public AssignmentConfiguration snapshot() throws IOException {
        String[] r = request("SNAPSHOT");
        if (r.length < 8 || !r[1].equals("ADMIN_ROUTING_SNAPSHOT")) throw new IOException("La captura de asignaciones no es válida.");
        int[] n = new int[6]; int[] widths = {5, 3, 3, 2, 2, 5}; long length = 8;
        for (int i = 0; i < 6; i++) {
            long count = number(r[2 + i], false);
            if (count > Integer.MAX_VALUE) throw new IOException("La cantidad de registros supera el rango permitido.");
            n[i] = (int) count; length += count * widths[i];
        }
        if (length != r.length) throw new IOException("La captura de asignaciones está incompleta.");
        int p = 8;
        List<OperatorOption> operators = new ArrayList<>(); List<WindowOption> windows = new ArrayList<>();
        List<ServiceOption> services = new ArrayList<>(); List<ServiceLink> operatorLinks = new ArrayList<>(), windowLinks = new ArrayList<>();
        List<AssignmentEntry> assignments = new ArrayList<>();
        for (int i = 0; i < n[0]; i++, p += 5) {
            if (!Set.of("ADMIN", "OPERADOR", "SUPERVISOR").contains(r[p + 2])) throw new IOException("Perfil de funcionario no válido.");
            operators.add(new OperatorOption(integer(r[p]), r[p + 1], r[p + 2], flag(r[p + 3]), flag(r[p + 4])));
        }
        for (int i = 0; i < n[1]; i++, p += 3) {
            if (!Set.of("ACTIVA", "INACTIVA", "PAUSADA").contains(r[p + 1]) || !Set.of("MIXED", "PRIORITY_ONLY").contains(r[p + 2])) throw new IOException("Estado o modalidad de ventanilla no válido.");
            windows.add(new WindowOption(integer(r[p]), r[p + 1], r[p + 2]));
        }
        for (int i = 0; i < n[2]; i++, p += 3) services.add(new ServiceOption(r[p], r[p + 1], flag(r[p + 2])));
        for (int i = 0; i < n[3]; i++, p += 2) operatorLinks.add(new ServiceLink(integer(r[p]), r[p + 1]));
        for (int i = 0; i < n[4]; i++, p += 2) windowLinks.add(new ServiceLink(integer(r[p]), r[p + 1]));
        for (int i = 0; i < n[5]; i++, p += 5) {
            LocalDateTime start = date(r[p + 3]), end = r[p + 4].isEmpty() ? null : date(r[p + 4]);
            if (end != null && end.isBefore(start)) throw new IOException("La asignación contiene fechas fuera de orden.");
            assignments.add(new AssignmentEntry(number(r[p], true), integer(r[p + 1]), integer(r[p + 2]), start, end));
        }
        return new AssignmentConfiguration(List.copyOf(operators), List.copyOf(windows), List.copyOf(services),
                List.copyOf(operatorLinks), List.copyOf(windowLinks), List.copyOf(assignments));
    }
    @Override public void operatorService(int id, String code, boolean enabled) throws IOException { link("OPERATOR_SERVICE", id, code, enabled); }
    @Override public void windowService(int id, String code, boolean enabled) throws IOException { link("WINDOW_SERVICE", id, code, enabled); }
    private void link(String command, int id, String code, boolean enabled) throws IOException {
        String[] r = request(command + ";" + id + ";" + encode(code) + ";" + enabled);
        if (r.length != 5 || !r[1].equals("ADMIN_ROUTING_" + command) || integer(r[2]) != id || !r[3].equals(code) || flag(r[4]) != enabled) {
            throw new IOException("La confirmación no corresponde a la habilitación solicitada.");
        }
    }
    @Override public long open(int operatorId, int windowId) throws IOException {
        return confirmation(request("OPEN_ASSIGNMENT;" + operatorId + ";" + windowId), "ASSIGNMENT_OPENED", 0);
    }
    @Override public void close(long assignmentId) throws IOException {
        confirmation(request("CLOSE_ASSIGNMENT;" + assignmentId), "ASSIGNMENT_CLOSED", assignmentId);
    }
    private static long confirmation(String[] r, String tag, long expected) throws IOException {
        if (r.length != 3 || !r[1].equals("ADMIN_ROUTING_" + tag)) throw new IOException("La confirmación no corresponde a la asignación solicitada.");
        long id = number(r[2], true);
        if (expected != 0 && id != expected) throw new IOException("Se confirmó otra asignación.");
        return id;
    }
    private static int integer(String value) throws IOException {
        long n = number(value, true); if (n > Integer.MAX_VALUE) throw new IOException("Identificador fuera de rango."); return (int) n;
    }
    private static long number(String value, boolean positive) throws IOException {
        try { long n = Long.parseLong(value); if (positive ? n > 0 : n >= 0) return n; } catch (NumberFormatException ignored) { }
        throw new IOException("Número recibido no válido.");
    }
    private static boolean flag(String value) throws IOException {
        if (!Set.of("true", "false").contains(value)) throw new IOException("Estado recibido no válido."); return Boolean.parseBoolean(value);
    }
    private static LocalDateTime date(String value) throws IOException {
        try { return LocalDateTime.parse(value); } catch (DateTimeParseException e) { throw new IOException("Fecha recibida no válida.", e); }
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private String[] request(String command) throws IOException {
        String line;
        try (Socket socket = NetworkSettings.open(host, port, 3000, 15000)) {
            try (var out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                    var in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                out.write("AUTH;" + session.token() + ";ADMIN_ROUTING_" + command); out.newLine(); out.flush(); line = in.readLine();
            }
        }
        if (line == null || line.isBlank()) throw new IOException("No se recibió una respuesta de configuración.");
        String[] r = line.split(";", -1);
        try { for (int i = 0; i < r.length; i++) r[i] = URLDecoder.decode(r[i], StandardCharsets.UTF_8); }
        catch (IllegalArgumentException e) { throw new IOException("Codificación de respuesta no válida.", e); }
        if (r.length == 3 && r[0].equals("DENIED")) throw new AccessDeniedException(r[1], r[2]);
        if (r.length == 2 && r[0].equals("ERROR")) throw new IllegalArgumentException(r[1]);
        if (r.length < 2 || !r[0].equals("OK")) throw new IOException("Respuesta de configuración no válida.");
        return r;
    }
}
