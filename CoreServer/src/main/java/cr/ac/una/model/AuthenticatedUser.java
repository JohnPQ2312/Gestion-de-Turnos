package cr.ac.una.model;

import java.util.Objects;

// Conserva la identidad y el rol que el servidor reconoce en una sesión válida.
public record AuthenticatedUser(
        int operatorId,
        String username,
        String name,
        UserRole role
        ) {

    public AuthenticatedUser {
        if (operatorId <= 0) {
            throw new IllegalArgumentException("El identificador del funcionario debe ser positivo.");
        }
        Objects.requireNonNull(username, "El nombre de usuario es obligatorio.");
        Objects.requireNonNull(name, "El nombre del funcionario es obligatorio.");
        Objects.requireNonNull(role, "El rol es obligatorio.");
    }
}
