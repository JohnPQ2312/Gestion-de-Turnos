package cr.ac.una.operatorsystem.client;

import cr.ac.una.operatorsystem.model.ManagedAppUser;
import cr.ac.una.operatorsystem.model.ManagedOperator;
import cr.ac.una.operatorsystem.model.UserSession;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

// Envía comandos autenticados de personas y convierte los listados y confirmaciones recibidos.
public class PeopleServerClient implements PeopleRequests {
    private final ProtocolTransport transport;
    private final UserSession session;

    public PeopleServerClient(String host, int port, UserSession session) {
        if (host == null || host.isBlank() || port < 1 || port > 65535) throw new IllegalArgumentException("Servidor o puerto no válido.");
        this.transport = new ProtocolTransport(host, port, new ProtocolTransport.Messages(
                "No se recibió una respuesta administrativa.",
                "Codificación de respuesta no válida.",
                "Respuesta administrativa no válida."));
        this.session = Objects.requireNonNull(session);
    }

    @Override
    public List<ManagedOperator> operators() throws IOException {
        String[] r = request("LIST_OPERATORS");
        int count = count(r, "OPERATORS", 7);
        List<ManagedOperator> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int p = 3 + i * 7;
            if (!Set.of("ADMIN", "OPERADOR", "SUPERVISOR").contains(r[p + 3])) throw new IOException("Rol de funcionario no válido.");
            rows.add(new ManagedOperator(id(r[p]), r[p + 1], r[p + 2], r[p + 3], flag(r[p + 4]), flag(r[p + 5]), flag(r[p + 6])));
        }
        return List.copyOf(rows);
    }

    @Override
    public List<ManagedAppUser> users() throws IOException {
        String[] r = request("LIST_USERS");
        int count = count(r, "USERS", 3);
        List<ManagedAppUser> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int p = 3 + i * 3;
            rows.add(new ManagedAppUser(id(r[p]), r[p + 1], flag(r[p + 2])));
        }
        return List.copyOf(rows);
    }

    @Override
    public int createOperator(String name, String username, String role, String password) throws IOException {
        return confirmation(request("CREATE_OPERATOR;" + encode(name) + ";" + encode(username) + ";" + role + ";" + encode(password)), "OPERATOR_CREATED", 0);
    }

    @Override
    public void updateOperator(int id, String name, String username, String role, boolean active) throws IOException {
        confirmation(request("UPDATE_OPERATOR;" + id + ";" + encode(name) + ";" + encode(username) + ";" + role + ";" + active), "OPERATOR_UPDATED", id);
    }

    @Override
    public void resetPassword(int id, String password) throws IOException {
        confirmation(request("RESET_PASSWORD;" + id + ";" + encode(password)), "PASSWORD_RESET", id);
    }

    @Override
    public int createUser(String username) throws IOException {
        return confirmation(request("CREATE_USER;" + encode(username)), "USER_CREATED", 0);
    }

    @Override
    public void updateUser(int id, String username, boolean active) throws IOException {
        confirmation(request("UPDATE_USER;" + id + ";" + encode(username) + ";" + active), "USER_UPDATED", id);
    }

    private static int confirmation(String[] r, String tag, int expectedId) throws IOException {
        if (r.length != 3 || !("ADMIN_PEOPLE_" + tag).equals(r[1])) throw new IOException("La confirmación administrativa no corresponde a la operación.");
        int id = id(r[2]);
        if (expectedId != 0 && id != expectedId) throw new IOException("La confirmación corresponde a otra persona.");
        return id;
    }

    private static int count(String[] r, String tag, int width) throws IOException {
        if (r.length < 3 || !("ADMIN_PEOPLE_" + tag).equals(r[1])) throw new IOException("El listado administrativo no es válido.");
        try {
            int count = Integer.parseInt(r[2]);
            if (count < 0 || r.length != 3L + (long) count * width) throw new IOException("El listado administrativo está incompleto.");
            return count;
        } catch (NumberFormatException e) { throw new IOException("La cantidad de personas no es válida.", e); }
    }

    private static int id(String value) throws IOException {
        try {
            int id = Integer.parseInt(value);
            if (id > 0) return id;
        } catch (NumberFormatException ignored) { }
        throw new IOException("El identificador recibido no es válido.");
    }

    private static boolean flag(String value) throws IOException {
        if (!"true".equals(value) && !"false".equals(value)) throw new IOException("El estado recibido no es válido.");
        return Boolean.parseBoolean(value);
    }

    private static String encode(String value) { return URLEncoder.encode(Objects.requireNonNull(value), StandardCharsets.UTF_8); }

    private String[] request(String message) throws IOException {
        return transport.authenticated(session.token(), "ADMIN_PEOPLE_" + message);
    }
}
