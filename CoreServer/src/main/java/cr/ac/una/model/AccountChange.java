package cr.ac.una.model;

// Solo vive en CoreServer: identifica la versión reemplazada, no sale por sockets.
public record AccountChange(OperatorAccount before, OperatorAccount after) {
    @Override
    public String toString() {
        return "AccountChange[" + after.operatorId() + "]";
    }
}
