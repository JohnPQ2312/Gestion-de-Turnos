package cr.ac.una.repository;

import cr.ac.una.model.AdministrationActor;
import cr.ac.una.model.ManagedService;
import cr.ac.una.model.ManagedWindow;
import java.util.List;

// Define las operaciones administrativas de servicios y ventanillas.
// Las implementaciones deben comprobar el acceso administrativo en cada escritura.
public interface CatalogAdministrationRepository {

    List<ManagedService> listServices(AdministrationActor actor);

    void createService(AdministrationActor actor, String code, String name, String prefix);

    void updateService(AdministrationActor actor, String code, String name, String prefix);

    void setServiceActive(AdministrationActor actor, String code, boolean active);

    List<ManagedWindow> listWindows(AdministrationActor actor);

    void createWindow(AdministrationActor actor, int id, String mode);

    void updateWindowMode(AdministrationActor actor, int id, String mode);

    // Devuelve el estado guardado: ACTIVA o INACTIVA.
    String setWindowActive(AdministrationActor actor, int id, boolean active);
}
