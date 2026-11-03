package cr.ac.una.costumerturn.model;

// Datos necesarios para mostrar la confirmación de un turno.
public record TicketConfirmation(String visualCode, String serviceName, boolean priority) {
}
