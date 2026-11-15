package cr.ac.una.repository;

import cr.ac.una.model.AttentionMode;
import cr.ac.una.model.OperatorWindowAssignment;
import cr.ac.una.model.CalledTicket;
import java.util.Optional;
import java.util.Set;

// Define el llamado de un turno compatible con la asignación y la modalidad de atención.
// Un resultado vacío indica que no hay un turno elegible en espera.
public interface TicketCallingRepository {

    // Devuelve también el identificador persistido del turno llamado.
    Optional<CalledTicket> callNext(
            OperatorWindowAssignment assignment,
            Set<String> eligibleServiceCodes,
            AttentionMode attentionMode
    );
}
