package cr.ac.una.repository;

import cr.ac.una.model.OperatorWindowAssignment;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// Permite consultar las asignaciones y su historial sin depender de una implementación concreta.
public interface OperatorWindowAssignmentRepository {

    // Historial completo, incluidas las asignaciones cerradas. Es la consulta administrativa: no se
    // usa para decidir qué asignaciones puede ver un funcionario.
    List<OperatorWindowAssignment> findAll();

    // Asignaciones de un funcionario que puede atender ahora: abiertas, ya iniciadas (inicio no
    // posterior a now), con el funcionario activo y la ventanilla operativa (ACTIVA o PAUSADA, esta
    // última para que pueda terminar su turno y reanudar). Una sola consulta, sin leer las de otros.
    List<OperatorWindowAssignment> findUsableByOperator(int operatorId, LocalDateTime now);

    Optional<OperatorWindowAssignment> findById(long assignmentId);
}
