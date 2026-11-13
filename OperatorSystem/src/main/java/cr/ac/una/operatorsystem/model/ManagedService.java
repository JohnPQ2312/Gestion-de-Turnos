package cr.ac.una.operatorsystem.model;

// Servicio del catálogo administrativo, tal como lo envía CoreServer.
public record ManagedService(String code, String name, String prefix, boolean active,
        long issuedTickets, long pendingTickets, long openRoutes, boolean prefixLocked) {
}
