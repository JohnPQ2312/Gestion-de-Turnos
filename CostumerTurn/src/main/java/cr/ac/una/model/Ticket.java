package cr.ac.una.model;

import java.time.LocalDateTime;
import java.util.Objects;

public class Ticket {

    private final Service service;
    private final int sequentialNumber;
    private final boolean priority;
    private final LocalDateTime createdAt;
    private TicketState state;
    private Integer windowId;
    private Integer operatorId;
    private LocalDateTime calledAt;

    public Ticket(Service service, int sequentialNumber, boolean priority) {
        this.service = Objects.requireNonNull(
                service, "El turno debe tener un servicio."
        );

        if (sequentialNumber <= 0) {
            throw new IllegalArgumentException(
                    "El número del turno debe ser mayor que cero."
            );
        }

        this.sequentialNumber = sequentialNumber;
        this.priority = priority;
        this.createdAt = LocalDateTime.now();
        this.state = TicketState.EN_ESPERA;
    }

    public void markCalled(int windowId, int operatorId) {
        if (state != TicketState.EN_ESPERA) {
            throw new IllegalStateException(
                    "Solo se puede asignar un turno que está en espera."
            );
        }

        if (windowId <= 0 || operatorId <= 0) {
            throw new IllegalArgumentException(
                    "La ventanilla y el operador deben tener identificadores positivos."
            );
        }

        this.windowId = windowId;
        this.operatorId = operatorId;
        this.calledAt = LocalDateTime.now();
        this.state = TicketState.LLAMADO;
    }

    public Service getService() {
        return service;
    }

    public int getSequentialNumber() {
        return sequentialNumber;
    }

    public boolean isPriority() {
        return priority;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public TicketState getState() {
        return state;
    }

    public Integer getWindowId() {
        return windowId;
    }

    public Integer getOperatorId() {
        return operatorId;
    }

    public LocalDateTime getCalledAt() {
        return calledAt;
    }

    public String getVisualCode() {
        return service.getPrefix()
                + String.format("%03d", sequentialNumber);
    }
}
