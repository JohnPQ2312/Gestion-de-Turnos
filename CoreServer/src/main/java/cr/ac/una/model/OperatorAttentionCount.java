package cr.ac.una.model;

// Cuenta los turnos finalizados por funcionario dentro del período del reporte.
public record OperatorAttentionCount(
        int operatorId,
        String operatorName,
        long attendedTickets
        ) {
}
