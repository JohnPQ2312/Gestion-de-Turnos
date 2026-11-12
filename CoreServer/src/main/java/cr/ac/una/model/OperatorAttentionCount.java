package cr.ac.una.model;

/** Consulta 2: cantidad de turnos finalizados por un funcionario. */
public record OperatorAttentionCount(
        int operatorId,
        String operatorName,
        long attendedTickets
        ) {
}
