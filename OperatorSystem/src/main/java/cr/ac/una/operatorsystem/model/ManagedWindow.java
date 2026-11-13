package cr.ac.una.operatorsystem.model;

// Ventanilla del catálogo administrativo. openAssignmentId es 0 si no tiene una asignación abierta.
public record ManagedWindow(int id, String state, String mode, long openAssignmentId,
        String operatorName, boolean hasActiveTicket, int enabledServices) {

    public static String modeLabel(String mode) {
        return "PRIORITY_ONLY".equals(mode) ? "Solo preferencial" : "Mixta";
    }

    public static String stateLabel(String state) {
        return switch (state) {
            case "ACTIVA" -> "Activa";
            case "PAUSADA" -> "En pausa";
            default -> "Dada de baja";
        };
    }
}
