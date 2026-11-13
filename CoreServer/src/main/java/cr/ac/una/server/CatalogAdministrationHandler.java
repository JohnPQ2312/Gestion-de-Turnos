package cr.ac.una.server;

import cr.ac.una.model.AdministrationActor;
import cr.ac.una.model.ManagedService;
import cr.ac.una.service.CatalogAdministrationService;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

// Comandos ADMIN_SERVICE_. TurnRequestHandler ya resolvió al ADMIN antes de llamar aquí.
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
            default -> throw new IllegalArgumentException("El comando de administración de servicios no es válido.");
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

    private static String decode(String value) { return URLDecoder.decode(value, StandardCharsets.UTF_8); }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static void size(String[] p, int expected) {
        if (p.length != expected) throw new IllegalArgumentException("El formato de la operación del catálogo no es válido.");
    }
    private static boolean flag(String value) {
        if (!"true".equals(value) && !"false".equals(value)) throw new IllegalArgumentException("El estado debe ser true o false.");
        return Boolean.parseBoolean(value);
    }
}
