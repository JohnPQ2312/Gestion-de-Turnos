package cr.ac.una.operatorsystem.model;

// Identifica una asignación disponible para que el funcionario atienda desde ella.
public record OperatorAssignment(long assignmentId, int operatorId, int windowId) {
}
