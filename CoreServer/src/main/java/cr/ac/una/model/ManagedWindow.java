package cr.ac.una.model;

// Fila del catálogo administrativo de ventanillas.
// openAssignmentId es 0 y operatorName vacío cuando no hay una asignación abierta.
public record ManagedWindow(int id, String state, String mode, long openAssignmentId,
        String operatorName, boolean hasActiveTicket, int enabledServices) {
}
