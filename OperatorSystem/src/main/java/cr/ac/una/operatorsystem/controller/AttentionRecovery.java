package cr.ac.una.operatorsystem.controller;

import cr.ac.una.operatorsystem.model.ActiveTicket;
import cr.ac.una.operatorsystem.model.OperatorAssignment;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

// Decide, sin JavaFX ni red, si la consulta del turno activo posterior a un fallo permite reconocer que la
// operación de atención sí se aplicó. Solo una IOException (respuesta perdida) puede recuperarse: los
// rechazos de negocio, DENIED y cualquier otra excepción se informan tal como llegaron. Nunca reintenta.
// OperatorController ejecuta las tareas, consulta GET_ACTIVE_TICKET y valida su pertenencia antes de aplicar
// esta política.
final class AttentionRecovery {

    enum Operation {
        CALL, START, FINISH, RECALL
    }

    private AttentionRecovery() {
    }

    // Solo considera recuperados los llamados o inicios que la consulta posterior permite reconocer.
    // La ausencia de turno activo no basta para confirmar un fin o un rellamado perdido.
    static boolean recovered(Operation operation, Long ticketId, Throwable cause, Optional<ActiveTicket> active) {
        if (!(cause instanceof IOException)) {
            return false;
        }

        return switch (operation) {
            case CALL ->
                active.isPresent();
            case START ->
                active.isPresent()
                && Objects.equals(active.get().ticketId(), ticketId)
                && "EN_ATENCION".equals(active.get().state());
            case FINISH, RECALL ->
                false;
        };
    }

    static String failureMessage(Operation operation) {
        return switch (operation) {
            case CALL ->
                "No se recibió la confirmación del llamado. "
                + "Compruebe el estado actual antes de repetir la solicitud.";
            case START ->
                "No se recibió la confirmación del inicio de atención. "
                + "Compruebe el estado actual antes de repetir la solicitud.";
            case RECALL ->
                "No se recibió la confirmación del rellamado. "
                + "Compruebe la pantalla pública antes de volver a solicitarlo.";
            case FINISH ->
                "No se recibió la confirmación de finalización. "
                + "Compruebe el estado y el historial del turno antes de repetir la solicitud.";
        };
    }

    // Un turno confirmado o consultado pertenece al puesto seleccionado solo si coinciden asignación,
    // funcionario y ventanilla.
    static boolean belongsTo(OperatorAssignment selected, long assignmentId, int operatorId, int windowId) {
        return assignmentId == selected.assignmentId()
                && operatorId == selected.operatorId()
                && windowId == selected.windowId();
    }
}
