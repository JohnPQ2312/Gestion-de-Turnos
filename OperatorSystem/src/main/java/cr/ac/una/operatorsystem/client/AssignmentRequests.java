package cr.ac.una.operatorsystem.client;

import cr.ac.una.operatorsystem.model.AssignmentConfiguration;
import java.io.IOException;

// Define las solicitudes que usa la vista de habilitaciones y asignaciones.
public interface AssignmentRequests {
    AssignmentConfiguration snapshot() throws IOException;
    void operatorService(int id, String code, boolean enabled) throws IOException;
    void windowService(int id, String code, boolean enabled) throws IOException;
    long open(int operatorId, int windowId) throws IOException;
    void close(long assignmentId) throws IOException;
}
