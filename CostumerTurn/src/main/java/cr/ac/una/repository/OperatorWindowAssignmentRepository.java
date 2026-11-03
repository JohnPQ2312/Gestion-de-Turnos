package cr.ac.una.repository;

import cr.ac.una.model.OperatorWindowAssignment;
import java.util.List;
import java.util.Optional;

public interface OperatorWindowAssignmentRepository {

    List<OperatorWindowAssignment> findAll();

    Optional<OperatorWindowAssignment> findById(long assignmentId);
}
