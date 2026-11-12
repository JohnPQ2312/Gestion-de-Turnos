package cr.ac.una.model;

// Usuario relacionado con tickets; no representa una cuenta de funcionario.
public record ManagedAppUser(int id, String username, boolean active) {
}
