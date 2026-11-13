package cr.ac.una.model;

// Conserva la cuenta anterior y la nueva para revocar las sesiones de la versión reemplazada.
// Estos datos permanecen en CoreServer.
public record AccountChange(OperatorAccount before, OperatorAccount after) {
    @Override
    public String toString() {
        return "AccountChange[" + after.operatorId() + "]";
    }
}
