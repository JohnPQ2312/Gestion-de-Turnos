package cr.ac.una.service;

import cr.ac.una.model.ServiceWindow;
import cr.ac.una.repository.WindowAvailabilityRepository;
import java.util.Objects;

/**
 * Pausa y reanudación de la ventanilla de una asignación (§4.3: indicar que
 * una ventanilla no está disponible temporalmente).
 *
 * Pausar no toca el turno en curso: si está LLAMADO o EN_ATENCION se puede
 * iniciar y finalizar. Solo impide llamar turnos nuevos. La autorización
 * (asignación propia) se comprueba antes, en AuthService.
 */
public class WindowAvailabilityService {

    private final WindowAvailabilityRepository repository;

    public WindowAvailabilityService(WindowAvailabilityRepository repository) {
        this.repository = Objects.requireNonNull(
                repository, "El repositorio de disponibilidad es obligatorio."
        );
    }

    public ServiceWindow currentState(long assignmentId) {
        requirePositive(assignmentId);
        return repository.findByAssignmentId(assignmentId).orElseThrow(()
                -> new IllegalArgumentException("No existe la asignación " + assignmentId + "."));
    }

    public ServiceWindow pause(long assignmentId) {
        requirePositive(assignmentId);
        return repository.pause(assignmentId);
    }

    public ServiceWindow resume(long assignmentId) {
        requirePositive(assignmentId);
        return repository.resume(assignmentId);
    }

    private static void requirePositive(long assignmentId) {
        if (assignmentId <= 0) {
            throw new IllegalArgumentException(
                    "El identificador de la asignación debe ser positivo."
            );
        }
    }
}
