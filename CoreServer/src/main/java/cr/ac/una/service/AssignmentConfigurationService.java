package cr.ac.una.service;

import cr.ac.una.model.AdministrationActor;
import cr.ac.una.model.AssignmentConfiguration;
import cr.ac.una.repository.AssignmentConfigurationRepository;
import java.util.Objects;

// Valida identificadores y códigos antes de delegar los cambios de habilitaciones y
// asignaciones.
public class AssignmentConfigurationService {
    private final AssignmentConfigurationRepository repository;
    public AssignmentConfigurationService(AssignmentConfigurationRepository repository) { this.repository = Objects.requireNonNull(repository); }
    public AssignmentConfiguration snapshot(AdministrationActor actor) { return repository.snapshot(actor); }
    public void operatorService(AdministrationActor actor, int id, String code, boolean enabled) {
        positive(id); repository.setOperatorService(actor, id, code(code), enabled);
    }
    public void windowService(AdministrationActor actor, int id, String code, boolean enabled) {
        positive(id); repository.setWindowService(actor, id, code(code), enabled);
    }
    public long open(AdministrationActor actor, int operatorId, int windowId) {
        positive(operatorId); positive(windowId); return repository.openAssignment(actor, operatorId, windowId);
    }
    public void close(AdministrationActor actor, long id) { positive(id); repository.closeAssignment(actor, id); }
    private static void positive(long id) {
        if (id <= 0) throw new IllegalArgumentException("Los identificadores deben ser positivos.");
    }
    private static String code(String value) {
        if (value == null || value.isBlank() || value.length() > 20 || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Seleccione un código de servicio válido de hasta 20 caracteres.");
        }
        return value.trim();
    }
}
