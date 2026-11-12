package cr.ac.una.model;

import java.time.LocalDateTime;

/**
 * Consultas 4 y 5: tiempo transcurrido entre dos momentos de un turno.
 *
 * En la consulta 4 va de la generación al inicio de la atención; en la 5,
 * del inicio al final de la atención.
 */
public record TicketDuration(
        String visualCode,
        String serviceName,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        long seconds
        ) {
}
