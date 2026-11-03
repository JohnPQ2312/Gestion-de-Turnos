package cr.ac.una.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

public class Ticket {

    private final Service service;
    private final int sequentialNumber;
    private final boolean priority;
    private final LocalDateTime createdAt;
    private TicketState state;
    private OperatorWindowAssignment assignment;
    private LocalDateTime calledAt;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

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

    public boolean isEligibleFor(
            Set<String> eligibleServiceCodes,
            AttentionMode attentionMode
    ) {
        Objects.requireNonNull(
                eligibleServiceCodes,
                "Los servicios habilitados son obligatorios."
        );

        Objects.requireNonNull(
                attentionMode,
                "La modalidad de atención es obligatoria."
        );

        return state == TicketState.EN_ESPERA
                && eligibleServiceCodes.contains(service.getCode())
                && attentionMode.allows(priority);
    }

    public void markCalled(OperatorWindowAssignment assignment) {
        if (state != TicketState.EN_ESPERA) {
            throw new IllegalStateException(
                    "Solo se puede asignar un turno que está en espera."
            );
        }

        Objects.requireNonNull(
                assignment, "La asignación es obligatoria."
        );

        if (!assignment.isOpen()) {
            throw new IllegalStateException(
                    "No se puede llamar un turno desde una asignación cerrada."
            );
        }

        LocalDateTime now = LocalDateTime.now();

        if (now.isBefore(createdAt)
                || now.isBefore(assignment.getStartedAt())) {
            throw new IllegalStateException(
                    "El llamado no puede ser anterior a la creación "
                    + "del turno ni al inicio de la asignación."
            );
        }

        this.assignment = assignment;
        this.calledAt = now;
        this.state = TicketState.LLAMADO;
    }

    public void startAttention() {
        if (state != TicketState.LLAMADO) {
            throw new IllegalStateException(
                    "Solo se puede iniciar la atención de un turno llamado."
            );
        }

        LocalDateTime now = LocalDateTime.now();

        if (now.isBefore(calledAt)) {
            throw new IllegalStateException(
                    "La atención no puede comenzar antes del llamado."
            );
        }

        this.startedAt = now;
        this.state = TicketState.EN_ATENCION;
    }

    public void finishAttention() {
        if (state != TicketState.EN_ATENCION) {
            throw new IllegalStateException(
                    "Solo se puede finalizar un turno que está en atención."
            );
        }

        LocalDateTime now = LocalDateTime.now();

        if (now.isBefore(startedAt)) {
            throw new IllegalStateException(
                    "La atención no puede finalizar antes de su inicio."
            );
        }

        this.endedAt = now;
        this.state = TicketState.FINALIZADO;
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

    public Long getAssignmentId() {
        return assignment == null
                ? null : assignment.getAssignmentId();
    }

    public Integer getWindowId() {
        return assignment == null
                ? null : assignment.getWindowId();
    }

    public Integer getOperatorId() {
        return assignment == null
                ? null : assignment.getOperatorId();
    }

    public LocalDateTime getCalledAt() {
        return calledAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public String getVisualCode() {
        return service.getPrefix()
                + String.format("%03d", sequentialNumber);
    }
}
