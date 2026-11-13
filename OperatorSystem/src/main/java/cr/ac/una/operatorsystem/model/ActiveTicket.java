package cr.ac.una.operatorsystem.model;

// Conserva la identidad y el estado del turno activo recuperado del servidor para esta
// asignación.
public record ActiveTicket(
        long ticketId,
        String visualCode,
        String serviceName,
        long assignmentId,
        int operatorId,
        int windowId,
        String state
        ) {

    public ActiveTicket {
        if (ticketId <= 0 || assignmentId <= 0 || operatorId <= 0 || windowId <= 0) {
            throw new IllegalArgumentException(
                    "Los identificadores del turno activo deben ser positivos."
            );
        }

        if (visualCode == null || visualCode.isBlank()
                || serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException(
                    "El turno activo debe tener código y nombre del servicio."
            );
        }

        if (!"LLAMADO".equals(state) && !"EN_ATENCION".equals(state)) {
            throw new IllegalArgumentException(
                    "El estado recibido para el turno activo no es válido."
            );
        }
    }
}
