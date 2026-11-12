package cr.ac.una.operatorsystem.model;

import java.util.Objects;

/**
 * Sesión iniciada en CoreServer. Solo vive en memoria; el token nunca se
 * guarda en archivos ni se muestra.
 */
public record UserSession(
        String token,
        int operatorId,
        String name,
        String username,
        String role
        ) {

    public UserSession {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("El token de sesión es obligatorio.");
        }
        if (operatorId <= 0) {
            throw new IllegalArgumentException("El identificador del funcionario no es válido.");
        }
        Objects.requireNonNull(name, "El nombre es obligatorio.");
        Objects.requireNonNull(username, "El usuario es obligatorio.");
        Objects.requireNonNull(role, "El rol es obligatorio.");
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    // Sin el token, para que no aparezca en registros ni mensajes.
    @Override
    public String toString() {
        return "UserSession[" + username + ", " + role + "]";
    }
}
