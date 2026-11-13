package cr.ac.una.server;

import cr.ac.una.model.AdministrationActor;
import cr.ac.una.service.AssignmentConfigurationService;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

// Traduce los comandos de habilitaciones y asignaciones a operaciones del servicio
// administrativo.
public class AssignmentConfigurationHandler {
    private final AssignmentConfigurationService service;
    public AssignmentConfigurationHandler(AssignmentConfigurationService service) { this.service = Objects.requireNonNull(service); }
    public String handle(String request, AdministrationActor actor) {
        String[] p = request.split(";", -1);
        return switch (p[0]) {
            case "ADMIN_ROUTING_SNAPSHOT" -> { size(p, 1); yield snapshot(actor); }
            case "ADMIN_ROUTING_OPERATOR_SERVICE", "ADMIN_ROUTING_WINDOW_SERVICE" -> {
                size(p, 4); int id = integer(p[1]); String code = URLDecoder.decode(p[2], StandardCharsets.UTF_8); boolean enabled = flag(p[3]);
                if (p[0].equals("ADMIN_ROUTING_OPERATOR_SERVICE")) service.operatorService(actor, id, code, enabled);
                else service.windowService(actor, id, code, enabled);
                yield "OK;" + p[0] + ";" + id + ";" + encode(code) + ";" + enabled;
            }
            case "ADMIN_ROUTING_OPEN_ASSIGNMENT" -> {
                size(p, 3); yield "OK;ADMIN_ROUTING_ASSIGNMENT_OPENED;" + service.open(actor, integer(p[1]), integer(p[2]));
            }
            case "ADMIN_ROUTING_CLOSE_ASSIGNMENT" -> {
                size(p, 2); long id = identifier(p[1]); service.close(actor, id); yield "OK;ADMIN_ROUTING_ASSIGNMENT_CLOSED;" + id;
            }
            default -> throw new IllegalArgumentException("El comando de configuración de atención no es válido.");
        };
    }
    private String snapshot(AdministrationActor actor) {
        var data = service.snapshot(actor);
        StringBuilder r = new StringBuilder("OK;ADMIN_ROUTING_SNAPSHOT");
        append(r, data.operators().size(), data.windows().size(), data.services().size(), data.operatorServices().size(), data.windowServices().size(), data.assignments().size());
        for (var o : data.operators()) append(r, o.id(), o.name(), o.role(), o.active(), o.hasCredential());
        for (var w : data.windows()) append(r, w.id(), w.state(), w.mode());
        for (var s : data.services()) append(r, s.code(), s.name(), s.active());
        for (var l : data.operatorServices()) append(r, l.ownerId(), l.serviceCode());
        for (var l : data.windowServices()) append(r, l.ownerId(), l.serviceCode());
        for (var a : data.assignments()) append(r, a.id(), a.operatorId(), a.windowId(), a.startedAt(), a.endedAt() == null ? "" : a.endedAt());
        return r.toString();
    }
    private static void append(StringBuilder r, Object... values) { for (Object value : values) r.append(';').append(encode(value.toString())); }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static void size(String[] p, int n) { if (p.length != n) throw new IllegalArgumentException("El formato de la solicitud de configuración no es válido."); }
    private static boolean flag(String p) {
        if (!p.equals("true") && !p.equals("false")) throw new IllegalArgumentException("La habilitación debe ser true o false.");
        return Boolean.parseBoolean(p);
    }
    private static long identifier(String p) {
        try { long id = Long.parseLong(p); if (id > 0) return id; } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("El identificador debe ser un entero positivo.");
    }
    private static int integer(String p) {
        long id = identifier(p); if (id > Integer.MAX_VALUE) throw new IllegalArgumentException("El identificador supera el rango permitido."); return (int) id;
    }
}
