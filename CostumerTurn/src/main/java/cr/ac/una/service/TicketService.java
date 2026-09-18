package cr.ac.una.service;

import cr.ac.una.model.Service;
import cr.ac.una.model.Ticket;
import cr.ac.una.repository.InMemoryTicketRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class TicketService {

    private final InMemoryTicketRepository ticketRepository;
    private final Map<String, Integer> lastNumbersByService = new HashMap<>();

    public TicketService(InMemoryTicketRepository ticketRepository) {
        this.ticketRepository = Objects.requireNonNull(
            ticketRepository, "El repositorio de turnos es obligatorio."
        );
    }

    public Ticket generateTicket(Service service, boolean priority) {
        if (service == null) {
            throw new IllegalArgumentException(
                "Debe seleccionar un servicio."
            );
        }

        String serviceCode = service.getCode();
        int nextNumber = lastNumbersByService.getOrDefault(serviceCode, 0) + 1;

        Ticket ticket = new Ticket(service, nextNumber, priority);

        ticketRepository.add(ticket);
        lastNumbersByService.put(serviceCode, nextNumber);

        return ticket;
    }
}