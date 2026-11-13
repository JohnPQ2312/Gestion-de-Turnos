package cr.ac.una.repository;

import cr.ac.una.model.AdministrationActor;
import cr.ac.una.model.ManagedService;
import java.util.List;

// Contrato de persistencia para la administración de servicios.
// Cada escritura revalida al ADMIN dentro de su propia transacción.
public interface CatalogAdministrationRepository {

    List<ManagedService> listServices(AdministrationActor actor);

    void createService(AdministrationActor actor, String code, String name, String prefix);

    void updateService(AdministrationActor actor, String code, String name, String prefix);

    void setServiceActive(AdministrationActor actor, String code, boolean active);
}
