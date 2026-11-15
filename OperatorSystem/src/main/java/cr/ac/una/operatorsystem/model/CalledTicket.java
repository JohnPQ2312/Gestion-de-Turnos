package cr.ac.una.operatorsystem.model;

// Confirmación completa de un llamado (CALL_NEXT_V2): identificador del turno, código, servicio,
// asignación, funcionario y ventanilla. El turno queda LLAMADO, igual que en ACTIVE_TICKET, de modo
// que la pantalla de atención puede continuar sin consultar otra vez el turno activo.
public record CalledTicket(
        long ticketId,
        String visualCode,
        String serviceName,
        long assignmentId,
        int operatorId,
        int windowId
        ) {

    public CalledTicket {
        if (ticketId <= 0 || assignmentId <= 0 || operatorId <= 0 || windowId <= 0) {
            throw new IllegalArgumentException("Los identificadores del llamado deben ser positivos.");
        }
        if (visualCode == null || visualCode.isBlank() || serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("El llamado debe tener código y nombre del servicio.");
        }
    }

    // El mismo turno, tal como lo devolvería GET_ACTIVE_TICKET justo después del llamado.
    public ActiveTicket toActiveTicket() {
        return new ActiveTicket(ticketId, visualCode, serviceName, assignmentId, operatorId, windowId, "LLAMADO");
    }
}
