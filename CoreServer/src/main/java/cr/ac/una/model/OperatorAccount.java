package cr.ac.una.model;

import java.util.Objects;

/**
 * Datos de acceso de un funcionario tal como están en la base.
 * passwordHash es null cuando el funcionario todavía no tiene credencial.
 * Nunca se envía al cliente.
 */
public record OperatorAccount(
        int operatorId,
        String username,
        String name,
        UserRole role,
        boolean active,
        String passwordHash
        ) {

    public OperatorAccount {
        if (operatorId <= 0) {
            throw new IllegalArgumentException("El identificador del funcionario debe ser positivo.");
        }
        Objects.requireNonNull(username, "El nombre de usuario es obligatorio.");
        Objects.requireNonNull(name, "El nombre del funcionario es obligatorio.");
        Objects.requireNonNull(role, "El rol es obligatorio.");
    }

    public boolean hasCredential() {
        return passwordHash != null && !passwordHash.isBlank();
    }
}
