package cr.ac.una.service;

import cr.ac.una.model.*;
import cr.ac.una.repository.PeopleAdministrationRepository;
import cr.ac.una.security.PasswordHasher;
import java.util.List;
import java.util.Objects;

// Valida datos de personas y prepara hashes antes de guardar las credenciales.
// Tras confirmar un cambio de cuenta, comunica sus dos versiones a la gestión de sesiones.
public class PeopleAdministrationService {
    private final PeopleAdministrationRepository repository;
    private final PasswordHasher hasher;
    private final AuthService auth;

    public PeopleAdministrationService(PeopleAdministrationRepository repository, PasswordHasher hasher, AuthService auth) {
        this.repository = Objects.requireNonNull(repository);
        this.hasher = Objects.requireNonNull(hasher);
        this.auth = Objects.requireNonNull(auth);
    }

    public List<ManagedOperator> operators(AdministrationActor actor) { return repository.listOperators(actor); }
    public List<ManagedAppUser> users(AdministrationActor actor) { return repository.listUsers(actor); }

    public int createOperator(AdministrationActor actor, String name, String username, String role, String password) {
        String checkedName = text(name, "nombre", 100);
        String checkedUser = username(username);
        UserRole checkedRole = role(role);
        return repository.createOperator(actor, checkedName, checkedUser, checkedRole, hash(password));
    }

    public void updateOperator(AdministrationActor actor, int id, String name, String username, String role, boolean active) {
        positive(id);
        AccountChange change = repository.updateOperator(actor, id, text(name, "nombre", 100), username(username), role(role), active);
        auth.accountChanged(change);
    }

    public void resetPassword(AdministrationActor actor, int id, String password) {
        positive(id);
        AccountChange change = repository.resetPassword(actor, id, hash(password));
        auth.accountChanged(change);
    }

    public int createUser(AdministrationActor actor, String username) {
        return repository.createUser(actor, username(username));
    }

    public void updateUser(AdministrationActor actor, int id, String username, boolean active) {
        positive(id);
        repository.updateUser(actor, id, username(username), active);
    }

    // Valida y deriva la contraseña; al terminar borra la copia temporal de caracteres.
    private String hash(String password) {
        char[] secret = password == null ? null : password.toCharArray();
        try { return hasher.hash(secret); } finally { PasswordHasher.clear(secret); }
    }

    private static void positive(int id) {
        if (id <= 0) throw new IllegalArgumentException("El identificador debe ser positivo.");
    }

    private static UserRole role(String role) {
        if (!"ADMIN".equals(role) && !"OPERADOR".equals(role)) {
            throw new IllegalArgumentException("Seleccione ADMIN u OPERADOR. SUPERVISOR todavía no tiene acceso.");
        }
        return UserRole.valueOf(role);
    }

    private static String username(String value) {
        String result = text(value, "nombre de usuario", 50);
        if (result.codePoints().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("El nombre de usuario no puede contener espacios.");
        }
        return result;
    }

    private static String text(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("El " + field + " es obligatorio.");
        String result = value.trim();
        if (result.codePointCount(0, result.length()) > max || result.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("El " + field + " admite hasta " + max + " caracteres y no permite caracteres de control.");
        }
        return result;
    }
}
