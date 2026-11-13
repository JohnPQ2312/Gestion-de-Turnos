package cr.ac.una.repository;

import cr.ac.una.model.ActiveTicket;
import java.util.Optional;

// Define la consulta del turno activo de una asignación para recuperar su estado confirmado.
public interface ActiveTicketRepository {

    Optional<ActiveTicket> findByAssignmentId(long assignmentId);
}
