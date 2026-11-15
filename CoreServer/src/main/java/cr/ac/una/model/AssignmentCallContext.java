package cr.ac.una.model;

import java.util.Objects;
import java.util.Set;

// Datos que el servicio revisa antes de abrir la transacción de llamado, leídos en una sola conexión:
// la asignación, si su funcionario está activo, su ventanilla (null si no existe) y los servicios que
// funcionario y ventanilla tienen habilitados en común (de cualquier estado, como antes).
// Son una lectura previa sin bloqueos: la transacción de llamado vuelve a comprobarlo todo.
public record AssignmentCallContext(
        OperatorWindowAssignment assignment,
        boolean operatorActive,
        ServiceWindow window,
        Set<String> sharedServiceCodes
        ) {

    public AssignmentCallContext {
        Objects.requireNonNull(assignment, "La asignación es obligatoria.");
        sharedServiceCodes = Set.copyOf(Objects.requireNonNull(
                sharedServiceCodes, "Los servicios compartidos son obligatorios."));
    }
}
