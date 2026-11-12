package cr.ac.una.repository;

import cr.ac.una.model.ServiceWindow;
import java.util.Optional;

/** Estado operativo de la ventanilla de una asignación: pausa y reanudación. */
public interface WindowAvailabilityRepository {

    /** Ventanilla de la asignación, con su estado actual. */
    Optional<ServiceWindow> findByAssignmentId(long assignmentId);

    /** ACTIVA → PAUSADA, en una transacción con la asignación bloqueada. */
    ServiceWindow pause(long assignmentId);

    /** PAUSADA → ACTIVA, en una transacción con la asignación bloqueada. */
    ServiceWindow resume(long assignmentId);
}
