package cr.ac.una.repository;

import cr.ac.una.model.Service;
import java.util.List;

public class InMemoryServiceRepository {

    private final List<Service> services = List.of(
        new Service("INFO", "Información general", "A"),
        new Service("TRAM", "Trámites", "B"),
        new Service("PAGO", "Pagos", "C")
    );

    public List<Service> findAll() {
        return services;
    }
}