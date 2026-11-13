package cr.ac.una.repository;

import cr.ac.una.model.AdministrationActor;
import cr.ac.una.model.AssignmentConfiguration;

// Define la consulta y modificación de habilitaciones y asignaciones de atención.
public interface AssignmentConfigurationRepository {
    AssignmentConfiguration snapshot(AdministrationActor actor);
    void setOperatorService(AdministrationActor actor, int operatorId, String serviceCode, boolean enabled);
    void setWindowService(AdministrationActor actor, int windowId, String serviceCode, boolean enabled);
    long openAssignment(AdministrationActor actor, int operatorId, int windowId);
    void closeAssignment(AdministrationActor actor, long assignmentId);
}
