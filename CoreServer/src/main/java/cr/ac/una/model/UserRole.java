package cr.ac.una.model;

/**
 * Perfil guardado en operator.operator_role.
 *
 * SUPERVISOR existe en el DDL pero todavía no tiene permisos: no se le
 * conceden los de ADMIN por semejanza.
 */
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
