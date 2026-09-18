package cr.ac.una.model;

import java.time.LocalDateTime;
import java.util.Objects;

public class Ticket {

    private final Service service;
    private final int sequentialNumber;
    private final boolean priority;
    private final LocalDateTime createdAt;
    private TicketState state;

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

    public String getVisualCode() {
        return service.getPrefix()
                + String.format("%03d", sequentialNumber);
    }
}