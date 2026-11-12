package cr.ac.una.model;

import java.time.LocalDateTime;
import java.util.List;

// Captura administrativa de catálogos, habilitaciones e historial.
public record AssignmentConfiguration(List<OperatorOption> operators, List<WindowOption> windows,
        List<ServiceOption> services, List<ServiceLink> operatorServices,
        List<ServiceLink> windowServices, List<AssignmentEntry> assignments) {
    public record OperatorOption(int id, String name, String role, boolean active, boolean hasCredential) { }
    public record WindowOption(int id, String state, String mode) { }
    public record ServiceOption(String code, String name, boolean active) { }
    public record ServiceLink(int ownerId, String serviceCode) { }
    public record AssignmentEntry(long id, int operatorId, int windowId, LocalDateTime startedAt, LocalDateTime endedAt) { }
}
