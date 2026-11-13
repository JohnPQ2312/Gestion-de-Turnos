package cr.ac.una.repository;

import cr.ac.una.model.ServiceWindow;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// Permite consultar ventanillas y los servicios habilitados para cada una.
public interface ServiceWindowRepository {

    List<ServiceWindow> findAll();

    Optional<ServiceWindow> findById(int windowId);

    Set<String> findServiceCodes(int windowId);
}