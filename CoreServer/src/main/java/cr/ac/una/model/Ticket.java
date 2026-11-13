package cr.ac.una.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

// Controla las transiciones y fechas de un turno: espera, llamado, atención y finalización.
// La asignación identifica al funcionario y la ventanilla responsables del llamado.
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

    public Ticket(
            Service service,
            int sequentialNumber,
            boolean priority
    ) {
        this(service, sequentialNumber, priority, LocalDateTime.now());
    }

    public Ticket(
            Service service,
            int sequentialNumber,
            boolean priority,
            LocalDateTime createdAt
    ) {
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
        this.createdAt = Objects.requireNonNull(
                createdAt, "La fecha de creación es obligatoria."
        );
        this.state = TicketState.EN_ESPERA;
    }

    // Reconstruye un turno activo desde MySQL y valida su secuencia de fechas.
    // No genera un consecutivo ni registra un llamado nuevo.
    public static Ticket restoreActive(
            Service service,
            int sequentialNumber,
            boolean priority,
            LocalDateTime createdAt,
            OperatorWindowAssignment assignment,
            TicketState state,
            LocalDateTime calledAt,
            LocalDateTime startedAt
    ) {
        Ticket ticket = new Ticket(service, sequentialNumber, priority, createdAt);
        Objects.requireNonNull(assignment, "La asignación del turno activo es obligatoria.");

        if (state != TicketState.LLAMADO && state != TicketState.EN_ATENCION) {
            throw new IllegalArgumentException(
                    "Solo se puede restaurar como activo un turno llamado o en atención."
            );
        }

        if (calledAt == null || calledAt.isBefore(createdAt)
                || calledAt.isBefore(assignment.getStartedAt())) {
            throw new IllegalArgumentException(
                    "La fecha de llamado debe existir y ser igual o posterior "
                    + "a la creación del turno y al inicio de la asignación."
            );
        }

        if (state == TicketState.LLAMADO && startedAt != null) {
            throw new IllegalArgumentException(
                    "Un turno llamado todavía no debe tener fecha de inicio de atención."
            );
        }

        if (state == TicketState.EN_ATENCION
                && (startedAt == null || startedAt.isBefore(calledAt))) {
            throw new IllegalArgumentException(
                    "Un turno en atención debe tener una fecha de inicio "
                    + "igual o posterior al llamado."
            );
        }

        ticket.assignment = assignment;
        ticket.state = state;
        ticket.calledAt = calledAt;
        ticket.startedAt = startedAt;
        return ticket;
    }

    // Exige espera, un servicio compartido y una prioridad admitida por la ventanilla.
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

    // Asocia el turno en espera a una asignación abierta y registra el primer llamado.
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

    // Solo permite pasar de LLAMADO a EN_ATENCION, con una fecha posterior o igual al llamado.
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

    // Solo finaliza un turno EN_ATENCION y conserva su fecha de cierre.
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

    // Combina el prefijo con el consecutivo, usando al menos tres dígitos.
    public String getVisualCode() {
        return service.getPrefix()
                + String.format("%03d", sequentialNumber);
    }
}
