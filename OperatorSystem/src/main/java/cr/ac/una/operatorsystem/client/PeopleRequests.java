package cr.ac.una.operatorsystem.client;

import cr.ac.una.operatorsystem.model.ManagedAppUser;
import cr.ac.una.operatorsystem.model.ManagedOperator;
import java.io.IOException;
import java.util.List;

// Define las solicitudes de funcionarios, credenciales y usuarios que utiliza la vista de
// Personas.
public interface PeopleRequests {
    List<ManagedOperator> operators() throws IOException;
    List<ManagedAppUser> users() throws IOException;
    int createOperator(String name, String username, String role, String password) throws IOException;
    void updateOperator(int id, String name, String username, String role, boolean active) throws IOException;
    void resetPassword(int id, String password) throws IOException;
    int createUser(String username) throws IOException;
    void updateUser(int id, String username, boolean active) throws IOException;
}
