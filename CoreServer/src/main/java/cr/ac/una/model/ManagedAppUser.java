package cr.ac.una.model;

// Representa un usuario relacionado con tickets, separado de las cuentas de funcionarios.
public record ManagedAppUser(int id, String username, boolean active) {
}
