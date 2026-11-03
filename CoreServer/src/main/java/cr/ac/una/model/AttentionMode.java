package cr.ac.una.model;

public enum AttentionMode {
    MIXED,
    PRIORITY_ONLY;

    public boolean allows(boolean priority) {
        return this == MIXED || priority;
    }
}