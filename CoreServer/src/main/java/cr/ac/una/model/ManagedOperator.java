package cr.ac.una.model;

// Datos administrativos de un funcionario, sin contraseña ni hash.
public record ManagedOperator(int id, String name, String username, UserRole role,
        boolean active, boolean hasCredential, boolean hasOpenAssignment) {
}
