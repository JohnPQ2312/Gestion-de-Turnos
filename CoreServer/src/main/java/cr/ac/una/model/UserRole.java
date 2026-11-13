package cr.ac.una.model;

// Representa los perfiles guardados en operator.operator_role.
// SUPERVISOR está definido en la base, pero la autorización actual no le concede acceso.
public enum UserRole {
    ADMIN,
    OPERADOR,
    SUPERVISOR;

    public static UserRole fromDatabase(String value) {
        try {
            return UserRole.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalStateException("El funcionario tiene un rol no reconocido.");
        }
    }
}
