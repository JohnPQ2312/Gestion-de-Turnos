package cr.ac.una.repository;

import cr.ac.una.model.OperatorAccount;
import java.util.Optional;

// Define la consulta de cuentas y el guardado de credenciales de funcionarios.
public interface AccountRepository {

    Optional<OperatorAccount> findByUsername(String username);

    Optional<OperatorAccount> findById(int operatorId);

    // Crea o reemplaza el hash de la contraseña de un funcionario existente.
    void saveCredential(int operatorId, String passwordHash);
}
