package cr.ac.una.QueueDisplayScreen.model;

import java.time.LocalDateTime;

// Conserva una fila pública y valida que su estado coincida con la ventanilla y el historial de
// llamados.
public record TicketRow(
        long ticketId,
        String visualCode,
        String serviceName,
        boolean priority,
        String state,
        Integer windowId,
        LocalDateTime calledAt,
        LocalDateTime lastCalledAt,
        int callCount
        ) {

    public TicketRow(long ticketId, String visualCode, String serviceName,
            boolean priority, String state, Integer windowId, LocalDateTime calledAt) {
        this(ticketId, visualCode, serviceName, priority, state, windowId,
                calledAt, calledAt, calledAt == null ? 0 : 1);
    }

    public TicketRow {
        if (ticketId <= 0) {
            throw new IllegalArgumentException(
                    "El identificador del turno debe ser positivo."
            );
        }

        if (visualCode == null || visualCode.isBlank()
                || serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException(
                    "El turno debe tener código y nombre del servicio."
            );
        }

        if (!"EN_ESPERA".equals(state)
                && !"LLAMADO".equals(state)
                && !"EN_ATENCION".equals(state)
                && !"FINALIZADO".equals(state)) {
            throw new IllegalArgumentException(
                    "El estado del turno no es válido."
            );
        }

        if ("EN_ESPERA".equals(state)) {
            if (windowId != null || calledAt != null || lastCalledAt != null || callCount != 0) {
                throw new IllegalArgumentException(
                        "Un turno en espera no debe tener ventanilla ni fecha de llamado."
                );
            }
        } else {
            if (windowId == null || windowId <= 0 || calledAt == null
                    || lastCalledAt == null || lastCalledAt.isBefore(calledAt) || callCount < 1) {
                throw new IllegalArgumentException(
                        "El turno debe conservar su ventanilla y fecha de llamado."
                );
            }
        }
    }
}
