package cr.ac.una.model;

/**
 * Consulta 3: turnos emitidos para un servicio en el período, en cualquier
 * estado. No representa turnos finalizados.
 */
public record ServiceTicketCount(
        String serviceCode,
        String serviceName,
        long issuedTickets
        ) {
}
