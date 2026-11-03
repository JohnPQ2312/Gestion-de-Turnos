package cr.ac.una.QueueDisplayScreen.model;

// Valores de una fila de la pantalla pública, sin comportamiento de negocio.
public record TicketRow(String visualCode, String serviceName, boolean priority, String state) {
}
