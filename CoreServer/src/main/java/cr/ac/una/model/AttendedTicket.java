package cr.ac.una.model;

import java.time.LocalDateTime;

// Representa un turno finalizado en el reporte de atenciones, con su funcionario y ventanilla.
public record AttendedTicket(
        String visualCode,
        String serviceName,
        boolean priority,
        int windowId,
        int operatorId,
        String operatorName,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime endedAt
        ) {
}
