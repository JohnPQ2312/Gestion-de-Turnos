package cr.ac.una.model;

import java.time.LocalDateTime;
import java.util.Objects;

// Contiene los datos públicos de un turno, incluido el primer y último llamado y su contador.
// Valida que el estado coincida con las fechas y la ventanilla recibidas.
public record DisplayTicket(
        long ticketId,
        String visualCode,
        String serviceName,
        boolean priority,
        TicketState state,
        Integer windowId,
        LocalDateTime calledAt,
        LocalDateTime lastCalledAt,
        int callCount
        ) {

    public DisplayTicket(long ticketId, String visualCode, String serviceName,
            boolean priority, TicketState state, Integer windowId, LocalDateTime calledAt) {
        this(ticketId, visualCode, serviceName, priority, state, windowId,
                calledAt, calledAt, calledAt == null ? 0 : 1);
    }

    public DisplayTicket {
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

        Objects.requireNonNull(state, "El estado del turno es obligatorio.");

        if (state == TicketState.EN_ESPERA) {
            if (windowId != null || calledAt != null || lastCalledAt != null || callCount != 0) {
                throw new IllegalArgumentException(
                        "Un turno en espera no debe tener ventanilla ni fecha de llamado."
                );
            }
        } else {
            if (windowId == null || windowId <= 0 || calledAt == null
                    || lastCalledAt == null || lastCalledAt.isBefore(calledAt) || callCount < 1) {
                throw new IllegalArgumentException(
                        "Un turno llamado debe conservar su ventanilla y fecha de llamado."
                );
            }
        }
    }
}
