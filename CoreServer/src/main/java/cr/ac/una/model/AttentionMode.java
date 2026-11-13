package cr.ac.una.model;

// Define si una ventanilla admite ambos tipos de turno o atiende solo preferenciales.
public enum AttentionMode {
    MIXED,
    PRIORITY_ONLY;

    // La modalidad mixta admite ambos tipos; la exclusiva exige un turno preferencial.
    public boolean allows(boolean priority) {
        return this == MIXED || priority;
    }
}