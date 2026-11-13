package cr.ac.una.repository;

import java.util.Set;

// Consulta si el funcionario está activo y qué servicios tiene habilitados.
public interface OperatorRepository {

    boolean isActive(int operatorId);

    Set<String> findServiceCodes(int operatorId);
}
