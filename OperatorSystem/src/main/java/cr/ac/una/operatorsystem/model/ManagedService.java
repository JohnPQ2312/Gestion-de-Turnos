package cr.ac.una.operatorsystem.model;

// Conserva el servicio y sus indicadores de uso tal como los confirma CoreServer.
public record ManagedService(String code, String name, String prefix, boolean active,
        long issuedTickets, long pendingTickets, long openRoutes, boolean prefixLocked) {
}
