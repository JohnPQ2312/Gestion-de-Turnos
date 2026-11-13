package cr.ac.una.config;

import cr.ac.una.model.OperatorAccount;
import cr.ac.una.model.UserRole;
import cr.ac.una.repository.JdbcAccountRepository;
import cr.ac.una.security.PasswordHasher;
import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Optional;

// Crea o restablece la credencial de un funcionario existente desde la consola.
// Sirve para preparar el primer acceso; las cuentas también se administran desde OperatorSystem.
// Lee la contraseña al ejecutar, sin recibirla como argumento ni guardarla en scripts.
// Se ejecuta junto a CoreServer con su db.properties; ver database/INICIALIZAR_ACCESO.md.
public final class CredentialSetup {

    private CredentialSetup() {
    }

    public static void main(String[] args) {
        try {
            JdbcAccountRepository accounts = new JdbcAccountRepository(new DatabaseConnectionFactory());
            Input input = Input.open();

            String username = input.readLine("Usuario del funcionario (operator_username): ").trim();
            Optional<OperatorAccount> found = accounts.findByUsername(username);
            if (found.isEmpty()) {
                System.err.println("No existe un funcionario con ese usuario. No se cambió nada.");
                return;
            }

            OperatorAccount account = found.get();
            System.out.println("Funcionario: " + account.name() + " | rol " + account.role()
                    + " | " + (account.active() ? "ACTIVO" : "INACTIVO")
                    + " | " + (account.hasCredential() ? "ya tiene contraseña: se reemplazará"
                    : "sin contraseña"));
            if (!account.active() || account.role() == UserRole.SUPERVISOR) {
                System.out.println("Aviso: con este estado o rol la cuenta no podrá iniciar sesión.");
            }

            char[] password = input.readPassword("Contraseña nueva (mínimo "
                    + PasswordHasher.MIN_LENGTH + " caracteres): ");
            char[] confirmation = input.readPassword("Repita la contraseña: ");
            try {
                if (!Arrays.equals(password, confirmation)) {
                    System.err.println("Las contraseñas no coinciden. No se cambió nada.");
                    return;
                }

                String hash = new PasswordHasher().hash(password);
                accounts.saveCredential(account.operatorId(), hash);
                System.out.println("Contraseña guardada para " + account.username() + ".");
                System.out.println("Si CoreServer está en ejecución, las sesiones abiertas de esta "
                        + "cuenta se rechazarán en su siguiente revalidación, cada 60 segundos de uso.");
            } finally {
                PasswordHasher.clear(password);
                PasswordHasher.clear(confirmation);
            }

        } catch (IllegalArgumentException exception) {
            System.err.println(exception.getMessage() + " No se cambió nada.");
        } catch (SQLException | IOException exception) {
            System.err.println("No se pudo completar: " + exception.getMessage());
        } catch (RuntimeException exception) {
            System.err.println("No se pudo completar: " + exception.getMessage());
        }
    }

    // Lee de la consola real si existe; si no (por ejemplo, NetBeans), de la entrada estándar.
    private interface Input {

        String readLine(String prompt) throws IOException;

        char[] readPassword(String prompt) throws IOException;

        static Input open() {
            Console console = System.console();
            if (console != null) {
                return new Input() {
                    @Override
                    public String readLine(String prompt) {
                        String line = console.readLine("%s", prompt);
                        return line == null ? "" : line;
                    }

                    @Override
                    public char[] readPassword(String prompt) {
                        char[] password = console.readPassword("%s", prompt);
                        return password == null ? new char[0] : password;
                    }
                };
            }

            System.out.println("Aviso: no hay una consola interactiva; la contraseña se verá "
                    + "mientras se escribe. Asegúrese de que nadie más vea la pantalla.");
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(System.in, StandardCharsets.UTF_8));
            return new Input() {
                @Override
                public String readLine(String prompt) throws IOException {
                    System.out.print(prompt);
                    System.out.flush();
                    String line = reader.readLine();
                    return line == null ? "" : line;
                }

                @Override
                public char[] readPassword(String prompt) throws IOException {
                    return readLine(prompt).toCharArray();
                }
            };
        }
    }
}
