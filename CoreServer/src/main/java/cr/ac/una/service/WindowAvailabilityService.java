package cr.ac.una.service;

import cr.ac.una.model.ServiceWindow;
import cr.ac.una.repository.WindowAvailabilityRepository;
import java.util.Objects;

// Consulta, pausa o reanuda la ventanilla de una asignación propia.
// La pausa permite completar el turno actual y detiene los llamados nuevos.
// AuthService comprueba la autorización antes de llegar a este servicio.
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
