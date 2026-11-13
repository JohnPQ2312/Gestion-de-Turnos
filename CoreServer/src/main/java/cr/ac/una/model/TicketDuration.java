package cr.ac.una.model;

import java.time.LocalDateTime;

// Representa una duración en segundos entre dos momentos de un turno.
// El reporte de espera mide creación a inicio; el de atención mide inicio a finalización.
public record TicketDuration(
        String visualCode,
        String serviceName,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        long seconds
        ) {
}
