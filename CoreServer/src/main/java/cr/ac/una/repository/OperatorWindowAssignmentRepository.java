package cr.ac.una.repository;

import cr.ac.una.model.OperatorWindowAssignment;
import java.util.List;
import java.util.Optional;

// Permite consultar las asignaciones y su historial sin depender de una implementación concreta.
public interface OperatorWindowAssignmentRepository {

    List<OperatorWindowAssignment> findAll();

    Optional<OperatorWindowAssignment> findById(long assignmentId);
}
