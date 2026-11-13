package cr.ac.una.model;

import java.util.Objects;

// Conserva los datos de acceso leídos de MySQL, incluido el hash de contraseña.
// passwordHash puede ser null si aún no existe credencial; la cuenta completa queda en
// CoreServer.
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
