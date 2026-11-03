package cr.ac.una.service;

import cr.ac.una.model.Service;
import cr.ac.una.model.Ticket;
import cr.ac.una.repository.TicketCreationRepository;
import java.util.Objects;

public class TicketService {

    private final TicketCreationRepository ticketRepository;

    public TicketService(TicketCreationRepository ticketRepository) {
        this.ticketRepository = Objects.requireNonNull(
                ticketRepository,
                "El repositorio de turnos es obligatorio."
        );
    }

    public Ticket generateTicket(Service service, boolean priority) {
        if (service == null) {
            throw new IllegalArgumentException(
                    "Debe seleccionar un servicio."
            );
        }

        return ticketRepository.createWithNextNumber(service, priority);
    }
}
