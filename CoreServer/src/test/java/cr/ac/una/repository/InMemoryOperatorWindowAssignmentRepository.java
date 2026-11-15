package cr.ac.una.repository;

import cr.ac.una.model.OperatorWindowAssignment;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// Prepara asignaciones de ejemplo para probar el flujo de atención sin consultar MySQL.
public class InMemoryOperatorWindowAssignmentRepository
        implements OperatorWindowAssignmentRepository {

    private final List<OperatorWindowAssignment> assignments;

    public InMemoryOperatorWindowAssignmentRepository() {
        LocalDateTime now = LocalDateTime.now();

        assignments = List.of(
                new OperatorWindowAssignment(
                        1L, 1, 1,
                        now.minusHours(1),
                        null
                ),
                new OperatorWindowAssignment(
                        2L, 2, 2,
                        now.minusHours(1),
                        null
                ),
                new OperatorWindowAssignment(
                        3L, 3, 3,
                        now.minusDays(1),
                        now.minusDays(1).plusHours(1)
                )
        );
    }

    @Override
    public List<OperatorWindowAssignment> findAll() {
        return assignments;
    }

    // Sin repositorios de funcionarios ni ventanillas no puede comprobar su estado: solo filtra por
    // funcionario, apertura e inicio. Es un ejemplo para probar el flujo, no la versión de producción.
    @Override
    public List<OperatorWindowAssignment> findUsableByOperator(int operatorId, LocalDateTime now) {
        return assignments.stream()
                .filter(assignment -> assignment.getOperatorId() == operatorId
                && assignment.isOpen() && !assignment.getStartedAt().isAfter(now))
                .toList();
    }

    @Override
    public Optional<OperatorWindowAssignment> findById(long assignmentId) {
        for (OperatorWindowAssignment assignment : assignments) {
            if (assignment.getAssignmentId() == assignmentId) {
                return Optional.of(assignment);
            }
        }

        return Optional.empty();
    }
}
