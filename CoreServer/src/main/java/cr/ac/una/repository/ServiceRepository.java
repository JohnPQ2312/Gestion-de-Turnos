package cr.ac.una.repository;

import cr.ac.una.model.Service;
import java.util.List;
import java.util.Optional;

// Permite listar servicios disponibles y buscarlos por su código estable.
public interface ServiceRepository {

    List<Service> findAll();

    Optional<Service> findByCode(String code);
}
