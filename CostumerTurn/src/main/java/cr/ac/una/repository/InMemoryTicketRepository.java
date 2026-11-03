package cr.ac.una.repository;

import cr.ac.una.model.Service;
import cr.ac.una.model.Ticket;
import cr.ac.una.model.AttentionMode;
import cr.ac.una.model.OperatorWindowAssignment;
import cr.ac.una.model.TicketState;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;

public class InMemoryTicketRepository implements TicketRepository {

    private final Queue<Ticket> regularQueue = new ArrayDeque<>();
    private final Queue<Ticket> priorityQueue = new ArrayDeque<>();

    private final Map<String, Integer> lastNumbersByService
            = new HashMap<>();

    @Override
    public synchronized Ticket createWithNextNumber(
            Service service,
            boolean priority
    ) {
        Objects.requireNonNull(
                service, "El servicio es obligatorio."
        );

        String serviceCode = service.getCode();
        int nextNumber
                = lastNumbersByService.getOrDefault(serviceCode, 0) + 1;

        Ticket ticket = new Ticket(service, nextNumber, priority);

        if (ticket.isPriority()) {
            priorityQueue.add(ticket);
        } else {
            regularQueue.add(ticket);
        }

        lastNumbersByService.put(serviceCode, nextNumber);

        return ticket;
    }

    @Override
    public synchronized Optional<Ticket> callNext(
            OperatorWindowAssignment assignment,
            Set<String> eligibleServiceCodes,
            AttentionMode attentionMode
    ) {
        Objects.requireNonNull(
                assignment, "La asignación es obligatoria."
        );
        Objects.requireNonNull(
                eligibleServiceCodes, "Los servicios habilitados son obligatorios."
        );
        Objects.requireNonNull(
                attentionMode, "La modalidad de atención es obligatoria."
        );

        if (!assignment.isOpen()
                || assignment.getStartedAt().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException(
                    "La asignación debe estar abierta y haber iniciado."
            );
        }

        if (hasActiveTicket(regularQueue, assignment)
                || hasActiveTicket(priorityQueue, assignment)) {
            throw new IllegalStateException(
                    "La asignación, el operador o la ventanilla "
                    + "ya tienen un turno activo."
            );
        }

        Optional<Ticket> next = findFirstEligible(
                priorityQueue, eligibleServiceCodes, attentionMode
        );

        if (next.isEmpty()) {
            next = findFirstEligible(
                    regularQueue, eligibleServiceCodes, attentionMode
            );
        }

        next.ifPresent(ticket -> ticket.markCalled(assignment));

        return next;
    }

    private Optional<Ticket> findFirstEligible(
            Queue<Ticket> tickets,
            Set<String> eligibleServiceCodes,
            AttentionMode attentionMode
    ) {
        for (Ticket ticket : tickets) {
            if (ticket.isEligibleFor(eligibleServiceCodes, attentionMode)) {
                return Optional.of(ticket);
            }
        }

        return Optional.empty();
    }

    private boolean hasActiveTicket(
            Queue<Ticket> tickets,
            OperatorWindowAssignment assignment
    ) {
        for (Ticket ticket : tickets) {
            boolean active = ticket.getState() == TicketState.LLAMADO
                    || ticket.getState() == TicketState.EN_ATENCION;

            if (active
                    && (ticket.getAssignmentId() == assignment.getAssignmentId()
                    || ticket.getOperatorId() == assignment.getOperatorId()
                    || ticket.getWindowId() == assignment.getWindowId())) {
                return true;
            }
        }

        return false;
    }

    @Override
    public synchronized List<Ticket> findRegularTickets() {
        return List.copyOf(regularQueue);
    }

    @Override
    public synchronized List<Ticket> findPriorityTickets() {
        return List.copyOf(priorityQueue);
    }
}
