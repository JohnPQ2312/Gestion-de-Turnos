package cr.ac.una.model;

public record ActiveTicket(
        long ticketId,
        String visualCode,
        String serviceName,
        long assignmentId,
        int operatorId,
        int windowId,
        TicketState state
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

        if (state != TicketState.LLAMADO && state != TicketState.EN_ATENCION) {
            throw new IllegalArgumentException(
                    "El turno activo debe estar llamado o en atención."
            );
        }
    }
}
