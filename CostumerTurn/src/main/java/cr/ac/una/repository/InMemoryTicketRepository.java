package cr.ac.una.repository;

import cr.ac.una.model.Ticket;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.Queue;

public class InMemoryTicketRepository {

    private final Queue<Ticket> regularQueue = new ArrayDeque<>();
    private final Queue<Ticket> priorityQueue = new ArrayDeque<>();

    public void add(Ticket ticket) {
        Objects.requireNonNull(ticket, "El turno no puede ser nulo.");

        if (ticket.isPriority()) {
            priorityQueue.add(ticket);
        } else {
            regularQueue.add(ticket);
        }
    }

    public List<Ticket> findRegularTickets() {
        return List.copyOf(regularQueue);
    }

    public List<Ticket> findPriorityTickets() {
        return List.copyOf(priorityQueue);
    }
}