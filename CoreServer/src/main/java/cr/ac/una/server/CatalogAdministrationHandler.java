package cr.ac.una.server;

import cr.ac.una.model.AdministrationActor;
import cr.ac.una.model.ManagedService;
import cr.ac.una.model.ManagedWindow;
import cr.ac.una.service.CatalogAdministrationService;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

// Traduce ADMIN_SERVICE_ y ADMIN_WINDOW_ al servicio de catálogo.
// TurnRequestHandler obtiene la identidad ADMIN antes de delegar la solicitud.
public class CatalogAdministrationHandler {
    private final CatalogAdministrationService service;

    public CatalogAdministrationHandler(CatalogAdministrationService service) {
        this.service = Objects.requireNonNull(service);
    }

    public String handle(String request, AdministrationActor actor) {
        String[] p = request.split(";", -1);
        return switch (p[0]) {
            case "ADMIN_SERVICE_LIST" -> { size(p, 1); yield services(actor); }
            case "ADMIN_SERVICE_CREATE" -> {
                size(p, 4);
                String code = decode(p[1]).trim();
                service.createService(actor, code, decode(p[2]), decode(p[3]));
                yield "OK;ADMIN_SERVICE_CREATED;" + encode(code);
            }
            case "ADMIN_SERVICE_UPDATE" -> {
                size(p, 4);
                String code = decode(p[1]);
                service.updateService(actor, code, decode(p[2]), decode(p[3]));
                yield "OK;ADMIN_SERVICE_UPDATED;" + encode(code);
            }
            case "ADMIN_SERVICE_SET_ACTIVE" -> {
                size(p, 3);
                String code = decode(p[1]);
                boolean active = flag(p[2]);
                service.setServiceActive(actor, code, active);
                yield "OK;ADMIN_SERVICE_STATE;" + encode(code) + ";" + active;
            }
            case "ADMIN_WINDOW_LIST" -> { size(p, 1); yield windows(actor); }
            case "ADMIN_WINDOW_CREATE" -> {
                size(p, 3);
                int id = id(p[1]);
                service.createWindow(actor, id, p[2]);
                yield "OK;ADMIN_WINDOW_CREATED;" + id;
            }
            case "ADMIN_WINDOW_UPDATE_MODE" -> {
                size(p, 3);
                int id = id(p[1]);
                service.updateWindowMode(actor, id, p[2]);
                yield "OK;ADMIN_WINDOW_UPDATED;" + id + ";" + p[2];
            }
            case "ADMIN_WINDOW_SET_ACTIVE" -> {
                size(p, 3);
                int id = id(p[1]);
                yield "OK;ADMIN_WINDOW_STATE;" + id + ";" + service.setWindowActive(actor, id, flag(p[2]));
            }
            default -> throw new IllegalArgumentException("El comando de administración del catálogo no es válido.");
        };
    }

    // OK;ADMIN_SERVICES;n; y por servicio: código;nombre;prefijo;activo;emitidos;pendientes;rutas_abiertas;prefijo_bloqueado
    private String services(AdministrationActor actor) {
        var rows = service.services(actor);
        StringBuilder r = new StringBuilder("OK;ADMIN_SERVICES;" + rows.size());
        for (ManagedService s : rows) {
            r.append(';').append(encode(s.code())).append(';').append(encode(s.name()))
                    .append(';').append(encode(s.prefix())).append(';').append(s.active())
                    .append(';').append(s.issuedTickets()).append(';').append(s.pendingTickets())
                    .append(';').append(s.openRoutes()).append(';').append(s.prefixLocked());
        }
        return r.toString();
    }

    // OK;ADMIN_WINDOWS;n; y por ventanilla: id;estado;modalidad;asignación_abierta(0 si no hay);funcionario;turno_activo;servicios_activos
    private String windows(AdministrationActor actor) {
        var rows = service.windows(actor);
        StringBuilder r = new StringBuilder("OK;ADMIN_WINDOWS;" + rows.size());
        for (ManagedWindow w : rows) {
            r.append(';').append(w.id()).append(';').append(w.state()).append(';').append(w.mode())
                    .append(';').append(w.openAssignmentId()).append(';').append(encode(w.operatorName()))
                    .append(';').append(w.hasActiveTicket()).append(';').append(w.enabledServices());
        }
        return r.toString();
    }

    private static String decode(String value) { return URLDecoder.decode(value, StandardCharsets.UTF_8); }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static void size(String[] p, int expected) {
        if (p.length != expected) throw new IllegalArgumentException("El formato de la operación del catálogo no es válido.");
    }
    private static boolean flag(String value) {
        if (!"true".equals(value) && !"false".equals(value)) throw new IllegalArgumentException("El estado debe ser true o false.");
        return Boolean.parseBoolean(value);
    }
    private static int id(String value) {
        try {
            int id = Integer.parseInt(value);
            if (id > 0) return id;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("El número de ventanilla debe ser un entero positivo.");
    }
}
