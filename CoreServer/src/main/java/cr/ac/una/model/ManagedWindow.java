package cr.ac.una.model;

// Describe el estado, modalidad y ocupación de una ventanilla para administrarla.
// Sin asignación abierta, openAssignmentId vale 0 y operatorName está vacío.
public record ManagedWindow(int id, String state, String mode, long openAssignmentId,
        String operatorName, boolean hasActiveTicket, int enabledServices) {
}
