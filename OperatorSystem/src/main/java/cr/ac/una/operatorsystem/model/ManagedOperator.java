package cr.ac.una.operatorsystem.model;

// Contiene los datos del funcionario y sus indicadores de credencial y asignación abierta.
public record ManagedOperator(int id, String name, String username, String role,
        boolean active, boolean hasCredential, boolean hasOpenAssignment) {
}
