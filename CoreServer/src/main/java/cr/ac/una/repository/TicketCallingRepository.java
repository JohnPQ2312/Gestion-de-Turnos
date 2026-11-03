package cr.ac.una.repository;

import cr.ac.una.model.AttentionMode;
import cr.ac.una.model.OperatorWindowAssignment;
import cr.ac.una.model.Ticket;
import java.util.Optional;
import java.util.Set;

public interface TicketCallingRepository {

    Optional<Ticket> callNext(
            OperatorWindowAssignment assignment,
            Set<String> eligibleServiceCodes,
            AttentionMode attentionMode
    );
}
