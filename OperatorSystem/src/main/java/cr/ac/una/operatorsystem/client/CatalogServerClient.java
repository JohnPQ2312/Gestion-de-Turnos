package cr.ac.una.operatorsystem.client;

import cr.ac.una.operatorsystem.model.ManagedService;
import cr.ac.una.operatorsystem.model.UserSession;
import cr.ac.una.operatorsystem.network.NetworkSettings;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

// Cliente de los comandos ADMIN_SERVICE_. Usa NetworkSettings (TLS fuera de loopback).
// Una confirmación que no coincide se informa como IOException: la escritura pudo guardarse y no se reenvía.
public class CatalogServerClient implements CatalogRequests {
    private static final int SERVICE_FIELDS = 8;
    private final String host;
    private final int port;
    private final UserSession session;

    public CatalogServerClient(String host, int port, UserSession session) {
        if (host == null || host.isBlank() || port < 1 || port > 65535) throw new IllegalArgumentException("Servidor o puerto no válido.");
        this.host = host;
        this.port = port;
        this.session = Objects.requireNonNull(session);
    }

    @Override
    public List<ManagedService> services() throws IOException {
        String[] r = request("ADMIN_SERVICE_LIST");
        int count = count(r, "ADMIN_SERVICES", SERVICE_FIELDS);
        List<ManagedService> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int p = 3 + i * SERVICE_FIELDS;
            if (r[p].isEmpty() || r[p + 1].isEmpty() || r[p + 2].isEmpty()) throw new IOException("El servicio recibido está incompleto.");
            rows.add(new ManagedService(r[p], r[p + 1], r[p + 2], flag(r[p + 3]),
                    number(r[p + 4], false), number(r[p + 5], false), number(r[p + 6], false), flag(r[p + 7])));
        }
        return List.copyOf(rows);
    }

    @Override
    public void createService(String code, String name, String prefix) throws IOException {
        String[] r = request("ADMIN_SERVICE_CREATE;" + encode(code) + ";" + encode(name) + ";" + encode(prefix));
        confirm(r, 3, "ADMIN_SERVICE_CREATED", code.trim());
    }

    @Override
    public void updateService(String code, String name, String prefix) throws IOException {
        String[] r = request("ADMIN_SERVICE_UPDATE;" + encode(code) + ";" + encode(name) + ";" + encode(prefix));
        confirm(r, 3, "ADMIN_SERVICE_UPDATED", code);
    }

    @Override
    public void setServiceActive(String code, boolean active) throws IOException {
        String[] r = request("ADMIN_SERVICE_SET_ACTIVE;" + encode(code) + ";" + active);
        confirm(r, 4, "ADMIN_SERVICE_STATE", code);
        if (flag(r[3]) != active) throw new IOException("La confirmación no corresponde al estado solicitado.");
    }

    private static void confirm(String[] r, int length, String tag, String expected) throws IOException {
        if (r.length != length || !r[1].equals(tag) || !r[2].equals(expected)) {
            throw new IOException("La confirmación no corresponde al registro solicitado.");
        }
    }

    private static int count(String[] r, String tag, int width) throws IOException {
        if (r.length < 3 || !r[1].equals(tag)) throw new IOException("El listado recibido no es válido.");
        long count = number(r[2], false);
        if (count > Integer.MAX_VALUE || 3 + count * width != r.length) throw new IOException("El listado recibido está incompleto.");
        return (int) count;
    }

    private static long number(String value, boolean positive) throws IOException {
        try {
            long n = Long.parseLong(value);
            if (positive ? n > 0 : n >= 0) return n;
        } catch (NumberFormatException ignored) { }
        throw new IOException("Número recibido no válido.");
    }

    private static boolean flag(String value) throws IOException {
        if (!"true".equals(value) && !"false".equals(value)) throw new IOException("Estado recibido no válido.");
        return Boolean.parseBoolean(value);
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    private String[] request(String command) throws IOException {
        String line;
        try (Socket socket = NetworkSettings.open(host, port, 3000, 15000)) {
            try (var out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                    var in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                out.write("AUTH;" + session.token() + ";" + command);
                out.newLine();
                out.flush();
                line = in.readLine();
            }
        }
        if (line == null || line.isBlank()) throw new IOException("No se recibió una respuesta del catálogo.");
        String[] r = line.split(";", -1);
        try {
            for (int i = 0; i < r.length; i++) r[i] = URLDecoder.decode(r[i], StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IOException("Codificación de respuesta no válida.", e);
        }
        if (r.length == 3 && r[0].equals("DENIED")) throw new AccessDeniedException(r[1], r[2]);
        if (r.length == 2 && r[0].equals("ERROR")) throw new IllegalArgumentException(r[1]);
        if (r.length < 2 || !r[0].equals("OK")) throw new IOException("Respuesta del catálogo no válida.");
        return r;
    }
}
