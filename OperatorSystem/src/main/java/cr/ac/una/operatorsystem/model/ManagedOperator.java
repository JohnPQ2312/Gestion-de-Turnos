package cr.ac.una.operatorsystem.model;

public record ManagedOperator(int id, String name, String username, String role,
        boolean active, boolean hasCredential, boolean hasOpenAssignment) {
}
