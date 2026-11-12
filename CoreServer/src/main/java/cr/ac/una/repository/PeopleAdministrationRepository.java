package cr.ac.una.repository;

import cr.ac.una.model.*;
import java.util.List;

public interface PeopleAdministrationRepository {
    List<ManagedOperator> listOperators(AdministrationActor actor);
    int createOperator(AdministrationActor actor, String name, String username, UserRole role, String passwordHash);
    AccountChange updateOperator(AdministrationActor actor, int id, String name, String username, UserRole role, boolean active);
    AccountChange resetPassword(AdministrationActor actor, int id, String passwordHash);
    List<ManagedAppUser> listUsers(AdministrationActor actor);
    int createUser(AdministrationActor actor, String username);
    void updateUser(AdministrationActor actor, int id, String username, boolean active);
}
