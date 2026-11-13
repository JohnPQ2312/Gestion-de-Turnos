package cr.ac.una.costumerturn.model;

// Reúne el código, servicio y prioridad confirmados por el servidor al emitir el turno.
public record TicketConfirmation(String visualCode, String serviceName, boolean priority) {
}
