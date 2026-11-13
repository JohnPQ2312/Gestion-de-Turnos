package cr.ac.una.operatorsystem.model;

// Conserva los datos de administración de una ventanilla recibidos de CoreServer.
// openAssignmentId vale 0 cuando no tiene asignación abierta.
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
