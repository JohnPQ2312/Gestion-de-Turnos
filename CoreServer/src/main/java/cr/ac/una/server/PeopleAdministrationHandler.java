package cr.ac.una.server;

import cr.ac.una.model.AdministrationActor;
import cr.ac.una.model.ManagedAppUser;
import cr.ac.una.model.ManagedOperator;
import cr.ac.una.service.PeopleAdministrationService;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

// Traduce los comandos administrativos de personas y codifica sus respuestas para el cliente.
public class PeopleAdministrationHandler {
    private final PeopleAdministrationService service;

    public PeopleAdministrationHandler(PeopleAdministrationService service) {
        this.service = Objects.requireNonNull(service);
    }

    public String handle(String request, AdministrationActor actor) {
        String[] p = request.split(";", -1);
        return switch (p[0]) {
            case "ADMIN_PEOPLE_LIST_OPERATORS" -> { length(p, 1); yield operators(actor); }
            case "ADMIN_PEOPLE_LIST_USERS" -> { length(p, 1); yield users(actor); }
            case "ADMIN_PEOPLE_CREATE_OPERATOR" -> {
                length(p, 5);
                int id = service.createOperator(actor, decode(p[1]), decode(p[2]), p[3], decode(p[4]));
                yield "OK;ADMIN_PEOPLE_OPERATOR_CREATED;" + id;
            }
            case "ADMIN_PEOPLE_UPDATE_OPERATOR" -> {
                length(p, 6);
                int id = id(p[1]);
                service.updateOperator(actor, id, decode(p[2]), decode(p[3]), p[4], active(p[5]));
                yield "OK;ADMIN_PEOPLE_OPERATOR_UPDATED;" + id;
            }
            case "ADMIN_PEOPLE_RESET_PASSWORD" -> {
                length(p, 3);
                int id = id(p[1]);
                service.resetPassword(actor, id, decode(p[2]));
                yield "OK;ADMIN_PEOPLE_PASSWORD_RESET;" + id;
            }
            case "ADMIN_PEOPLE_CREATE_USER" -> {
                length(p, 2);
                yield "OK;ADMIN_PEOPLE_USER_CREATED;" + service.createUser(actor, decode(p[1]));
            }
            case "ADMIN_PEOPLE_UPDATE_USER" -> {
                length(p, 4);
                int id = id(p[1]);
                service.updateUser(actor, id, decode(p[2]), active(p[3]));
                yield "OK;ADMIN_PEOPLE_USER_UPDATED;" + id;
            }
            default -> throw new IllegalArgumentException("El comando administrativo de personas no es válido.");
        };
    }

    private String operators(AdministrationActor actor) {
        List<ManagedOperator> rows = service.operators(actor);
        StringBuilder result = new StringBuilder("OK;ADMIN_PEOPLE_OPERATORS;" + rows.size());
        for (ManagedOperator row : rows) {
            result.append(';').append(row.id()).append(';').append(encode(row.name()))
                    .append(';').append(encode(row.username())).append(';').append(row.role())
                    .append(';').append(row.active()).append(';').append(row.hasCredential())
                    .append(';').append(row.hasOpenAssignment());
        }
        return result.toString();
    }

    private String users(AdministrationActor actor) {
        List<ManagedAppUser> rows = service.users(actor);
        StringBuilder result = new StringBuilder("OK;ADMIN_PEOPLE_USERS;" + rows.size());
        for (ManagedAppUser row : rows) result.append(';').append(row.id()).append(';')
                .append(encode(row.username())).append(';').append(row.active());
        return result.toString();
    }

    private static String decode(String value) { return URLDecoder.decode(value, StandardCharsets.UTF_8); }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static void length(String[] p, int expected) {
        if (p.length != expected) throw new IllegalArgumentException("El formato de la operación de personas no es válido.");
    }
    private static boolean active(String value) {
        if (!"true".equals(value) && !"false".equals(value)) throw new IllegalArgumentException("El estado debe ser true o false.");
        return Boolean.parseBoolean(value);
    }
    private static int id(String value) {
        try {
            int id = Integer.parseInt(value);
            if (id > 0) return id;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("El identificador debe ser un entero positivo.");
    }
}
