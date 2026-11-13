package cr.ac.una.model;

// Reúne los datos administrativos del funcionario y sus indicadores de acceso y asignación.
// La respuesta excluye contraseñas y hashes.
public record ManagedOperator(int id, String name, String username, UserRole role,
        boolean active, boolean hasCredential, boolean hasOpenAssignment) {
}
