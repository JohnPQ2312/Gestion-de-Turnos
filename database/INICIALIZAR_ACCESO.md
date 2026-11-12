# Inicializar el acceso al sistema

OperatorSystem exige iniciar sesión. Ningún funcionario tiene contraseña por defecto: cada credencial se crea a propósito con la herramienta `CredentialSetup` de CoreServer.

## 1. Estructura

- **Base existente** (por ejemplo, la del equipo): aplicar una vez `database/migraciones/001_credenciales_operador.sql`. Solo crea la tabla `operator_credential`; no modifica registros.
- **Instalación nueva**: `database/script borrador.sql` ya incluye esa tabla. No aplicar además la migración.

## 2. Crear la primera cuenta administrativa

1. Debe existir un funcionario con `operator_role = 'ADMIN'` y `operator_state = 'ACTIVO'`. Si no existe, crearlo en `operator` con un nombre de usuario propio (sin contraseña; la tabla `operator` no la guarda).
2. Con `CoreServer/src/main/resources/db.properties` configurado, ejecutar la clase `cr.ac.una.config.CredentialSetup`:
   - NetBeans: abrir el archivo `CredentialSetup.java` → clic derecho → **Run File**. Escribir los datos en la ventana Output.
   - Consola con el JDK: la contraseña no se muestra mientras se escribe.
3. Indicar el usuario y escribir dos veces la contraseña (8 a 128 caracteres).

La contraseña nunca va como argumento del programa, ni en scripts SQL, ni en Git. Repetir el paso 2 para cada funcionario que deba entrar a OperatorSystem; también sirve para restablecer una contraseña.

Si NetBeans no ofrece una consola interactiva, la herramienta avisa que la contraseña será visible mientras se escribe.

## 3. Permisos

| Perfil | Puede |
| --- | --- |
| Sin sesión | Catálogo y emisión (CostumerTurn), pantalla pública (QueueDisplayScreen) |
| OPERADOR | Ver y operar únicamente sus propias asignaciones abiertas |
| ADMIN | Las cinco consultas. Atender solo si tiene una asignación propia |
| SUPERVISOR | Sin acceso por ahora |

Un funcionario `INACTIVO`, o sin credencial, no puede iniciar sesión.

## 4. Sesiones

- El token vive solo en la memoria de CoreServer y de OperatorSystem; no se guarda en archivos.
- Vence tras 30 minutos sin uso o 8 horas desde el inicio. El botón **Cerrar sesión** la termina de inmediato.
- Cada 60 segundos CoreServer vuelve a leer el estado y el rol del funcionario: una baja o un cambio de rol hacen efecto en la siguiente verificación.
- Al reiniciar CoreServer todas las sesiones terminan y OperatorSystem vuelve a la pantalla de acceso.

## 5. Protocolo

| Solicitud | Respuesta correcta |
| --- | --- |
| `LOGIN;<usuario>;<contraseña>` (textos codificados) | `OK;LOGIN;<token>;<id>;<nombre>;<usuario>;<rol>` |
| `AUTH;<token>;LOGOUT` | `OK;LOGOUT` |
| `AUTH;<token>;<comando protegido>` | La respuesta normal del comando |

Comandos protegidos: `LIST_ASSIGNMENTS` (solo devuelve las asignaciones propias), `CALL_NEXT`, `GET_ACTIVE_TICKET`, `START_ATTENTION`, `FINISH_ATTENTION` (solo sobre asignación propia) y los cinco `REPORT_*` (solo ADMIN). Enviados sin `AUTH` se rechazan; no existe un modo anónimo.

Los rechazos de acceso responden `DENIED;<motivo>;<mensaje>`, distinto de `ERROR;<mensaje>` de las reglas de negocio. Motivos: `INVALID_CREDENTIALS`, `ACCOUNT_DISABLED`, `UNAUTHENTICATED`, `SESSION_EXPIRED`, `FORBIDDEN`.

Las herramientas de `pruebas/` todavía envían los comandos de atención sin sesión; deben adaptarse a este contrato.

Con la comunicación local (127.0.0.1) las credenciales no salen del equipo. Si se usa la red, el bloque de configuración de red debe agregar un transporte protegido antes de enviar contraseñas fuera del equipo.
