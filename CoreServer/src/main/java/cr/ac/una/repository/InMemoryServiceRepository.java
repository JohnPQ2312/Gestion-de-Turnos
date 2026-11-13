package cr.ac.una.repository;

import cr.ac.una.model.Service;
import java.util.List;
import java.util.Optional;

// Ofrece el catálogo estático de servicios usado por el flujo en memoria.
public class InMemoryServiceRepository implements ServiceRepository {

    private final List<Service> services = List.of(
            new Service("INFO", "Información general", "A"),
            new Service("TRAM", "Trámites", "B"),
            new Service("PAGO", "Pagos", "C")
    );

    @Override
    public List<Service> findAll() {
        return services;
    }

    @Override
    public Optional<Service> findByCode(String code) {
        for (Service service : services) {
            if (service.getCode().equals(code)) {
                return Optional.of(service);
            }
        }

        return Optional.empty();
    }
}
