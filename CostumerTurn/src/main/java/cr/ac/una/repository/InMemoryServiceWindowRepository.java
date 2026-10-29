package cr.ac.una.repository;

import cr.ac.una.model.AttentionMode;
import cr.ac.una.model.ServiceWindow;
import cr.ac.una.model.WindowState;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class InMemoryServiceWindowRepository
        implements ServiceWindowRepository {

    private final List<ServiceWindow> windows = List.of(
            new ServiceWindow(
                    1, WindowState.ACTIVA, AttentionMode.MIXED
            ),
            new ServiceWindow(
                    2, WindowState.ACTIVA, AttentionMode.PRIORITY_ONLY
            ),
            new ServiceWindow(
                    3, WindowState.INACTIVA, AttentionMode.MIXED
            )
    );

    private final Map<Integer, Set<String>> serviceCodesByWindow = Map.of(
            1, Set.of("INFO", "TRAM", "PAGO"),
            2, Set.of("INFO", "TRAM", "PAGO"),
            3, Set.of("PAGO")
    );

    @Override
    public List<ServiceWindow> findAll() {
        return windows;
    }

    @Override
    public Optional<ServiceWindow> findById(int windowId) {
        for (ServiceWindow window : windows) {
            if (window.getWindowId() == windowId) {
                return Optional.of(window);
            }
        }

        return Optional.empty();
    }

    @Override
    public Set<String> findServiceCodes(int windowId) {
        return serviceCodesByWindow.getOrDefault(windowId, Set.of());
    }
}