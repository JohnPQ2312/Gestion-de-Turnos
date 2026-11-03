package cr.ac.una.repository;

import cr.ac.una.model.ActiveTicket;
import java.util.Optional;

public interface ActiveTicketRepository {

    Optional<ActiveTicket> findByAssignmentId(long assignmentId);
}
