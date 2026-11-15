package cr.ac.una.repository;

import cr.ac.una.model.AssignmentCallContext;
import java.util.Optional;

// Lectura previa al llamado en una única conexión. Vacío si la asignación no existe.
public interface AssignmentCallContextRepository {

    Optional<AssignmentCallContext> findCallContext(long assignmentId);
}
