package cr.ac.una.operatorsystem.model;

// Contiene el código, servicio, funcionario y ventanilla confirmados al llamar un turno.
public record CalledTicket(String visualCode, String serviceName, int operatorId, int windowId) {
}
