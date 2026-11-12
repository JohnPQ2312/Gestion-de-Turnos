package cr.ac.una.model;

// Identidad interna obtenida de una sesión ADMIN; nunca se recibe del cliente.
public record AdministrationActor(int operatorId, String credentialFingerprint) {
    public AdministrationActor {
        if (operatorId <= 0 || credentialFingerprint == null || credentialFingerprint.isBlank()) {
            throw new IllegalArgumentException("La identidad administrativa no es válida.");
        }
    }

    @Override
    public String toString() {
        return "AdministrationActor[" + operatorId + "]";
    }
}
