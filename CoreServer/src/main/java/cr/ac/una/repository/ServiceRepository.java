package cr.ac.una.repository;

import cr.ac.una.model.Service;
import java.util.List;
import java.util.Optional;

public interface ServiceRepository {

    List<Service> findAll();

    Optional<Service> findByCode(String code);
}
