package cr.ac.una.repository;

import cr.ac.una.model.Service;
import cr.ac.una.model.Ticket;
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
    public synchronized List<Ticket> findRegularTickets() {
        return List.copyOf(regularQueue);
    }

    @Override
    public synchronized List<Ticket> findPriorityTickets() {
        return List.copyOf(priorityQueue);
    }
}
