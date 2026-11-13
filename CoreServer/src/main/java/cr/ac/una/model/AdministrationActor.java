package cr.ac.una.model;

// Identifica al administrador a partir de su sesión y de la huella de su credencial.
// El servidor construye este dato antes de entrar al repositorio.
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
