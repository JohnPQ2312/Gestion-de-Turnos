package cr.ac.una.model;

import java.time.LocalDateTime;

/** Consulta 1: un turno finalizado, con quién y dónde lo atendió. */
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
