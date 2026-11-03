package cr.ac.una.repository;

import cr.ac.una.model.AttentionMode;
import cr.ac.una.model.OperatorWindowAssignment;
import cr.ac.una.model.Service;
import cr.ac.una.model.Ticket;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface TicketRepository {

    Ticket createWithNextNumber(Service service, boolean priority);

    Optional<Ticket> callNext(
            OperatorWindowAssignment assignment,
            Set<String> eligibleServiceCodes,
            AttentionMode attentionMode
    );

    List<Ticket> findRegularTickets();

    List<Ticket> findPriorityTickets();
}
