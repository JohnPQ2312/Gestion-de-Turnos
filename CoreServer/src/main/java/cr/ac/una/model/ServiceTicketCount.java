package cr.ac.una.model;

// Cuenta turnos emitidos por servicio durante el período, cualquiera que sea su estado.
public record ServiceTicketCount(
        String serviceCode,
        String serviceName,
        long issuedTickets
        ) {
}
