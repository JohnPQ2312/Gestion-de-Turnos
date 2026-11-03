package cr.ac.una.repository;

import java.util.Set;

public interface OperatorRepository {

    boolean isActive(int operatorId);

    Set<String> findServiceCodes(int operatorId);
}
