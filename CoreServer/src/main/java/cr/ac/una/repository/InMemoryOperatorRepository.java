package cr.ac.una.repository;

import java.util.Map;
import java.util.Set;

// Ofrece funcionarios y habilitaciones estáticos para trabajar sin MySQL.
// Los datos de esta implementación se limitan a la memoria del proceso.
public class InMemoryOperatorRepository implements OperatorRepository {

    private final Set<Integer> activeOperatorIds = Set.of(1, 2);

    private final Map<Integer, Set<String>> serviceCodesByOperator = Map.of(
            1, Set.of("INFO", "TRAM", "PAGO"),
            2, Set.of("INFO", "TRAM"),
            3, Set.of("PAGO")
    );

    @Override
    public boolean isActive(int operatorId) {
        return activeOperatorIds.contains(operatorId);
    }

    @Override
    public Set<String> findServiceCodes(int operatorId) {
        return serviceCodesByOperator.getOrDefault(operatorId, Set.of());
    }
}
