# Inicializar el acceso al sistema

OperatorSystem exige iniciar sesión. Ningún funcionario tiene contraseña por defecto: cada credencial se crea a propósito con la herramienta `CredentialSetup` de CoreServer.

## 1. Estructura

- **Base existente** (por ejemplo, la del equipo): aplicar una vez `database/migraciones/001_credenciales_operador.sql` y después `002_estado_pausada_ventanilla.sql`. La primera crea `operator_credential`; la segunda permite `PAUSADA`. Conservan los registros existentes.
- **Instalación nueva**: `database/script borrador.sql` ya incluye esa tabla. No aplicar además la migración.

La versión con rellamado también requiere la [migración 003](migraciones/003_rellamado_ticket.sql) sobre una base anterior. Consultar [RELLAMADO.md](RELLAMADO.md) para aplicarla con CoreServer detenido. En una instalación nueva el DDL ya la incluye.

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
- Al reiniciar CoreServer todas las sesiones terminan y OperatorSystem vuelve a la pantalla de acceso.

### Revocación por cambios en la cuenta

Cada sesión guarda, solo en la memoria del servidor, una huella (SHA-256) del hash de la contraseña con que se inició. En una solicitud protegida, si pasaron al menos 60 segundos desde la última verificación de esa sesión, CoreServer vuelve a leer la cuenta. No hay una tarea que revise las sesiones sin solicitudes. La sesión se revoca si:

- el funcionario quedó `INACTIVO`, pasó a un rol sin acceso o perdió su credencial;
- la contraseña cambió (la huella ya no coincide). En ese caso se revocan las sesiones que usaron esa credencial anterior; se conservan las iniciadas con la nueva contraseña.

Después de restablecer una contraseña con `CredentialSetup`, la contraseña anterior ya no permite entrar y la nueva sí. Un token anterior puede seguir autorizando hasta que corresponda su siguiente revalidación; la primera solicitud protegida después de ese plazo detecta el cambio y lo rechaza. La herramienta se ejecuta en otro proceso, por eso no puede cerrar las sesiones del servidor en el acto; la administración de usuarios (bloque 5) podrá revocarlas de inmediato con `AuthService.revokeSessions`.

### Cierre al salir de OperatorSystem

Al cerrar la ventana con una sesión abierta, el aviso de `LOGOUT` se envía en otro hilo, con un plazo de 2 segundos para conectar y 2 para la respuesta. La interfaz no espera ese aviso. Si el servidor confirma, el token queda revocado; si no responde a tiempo, la sesión **no** se considera revocada: vence en CoreServer por inactividad (30 minutos) o al reiniciarlo.

## 5. Protocolo

| Solicitud | Respuesta correcta |
| --- | --- |
| `LOGIN;<usuario>;<contraseña>` (textos codificados) | `OK;LOGIN;<token>;<id>;<nombre>;<usuario>;<rol>` |
| `AUTH;<token>;LOGOUT` | `OK;LOGOUT` |
| `AUTH;<token>;<comando protegido>` | La respuesta normal del comando |

Comandos protegidos: `LIST_ASSIGNMENTS` (solo devuelve las asignaciones propias), `CALL_NEXT`, `GET_ACTIVE_TICKET`, `START_ATTENTION`, `FINISH_ATTENTION`, `RECALL_TICKET`, `GET_WINDOW_STATE`, `PAUSE_WINDOW`, `RESUME_WINDOW` (solo sobre asignación propia) y los cinco `REPORT_*` (solo ADMIN). Enviados sin `AUTH` se rechazan; no existe un modo anónimo.

Pausa de la ventanilla (migración 002): `GET_WINDOW_STATE;<asignación>`, `PAUSE_WINDOW;<asignación>` y `RESUME_WINDOW;<asignación>` responden `OK;WINDOW_STATE;<ventanilla>;<ACTIVA|PAUSADA|INACTIVA>`. En `PAUSADA` se rechaza `CALL_NEXT`, pero el turno en curso puede iniciarse y finalizarse; la asignación sigue en `LIST_ASSIGNMENTS`. Reanudar solo pasa de `PAUSADA` a `ACTIVA`; una ventanilla `INACTIVA` (baja administrativa) no se pausa ni se reanuda.

Los rechazos de acceso responden `DENIED;<motivo>;<mensaje>`, distinto de `ERROR;<mensaje>` de las reglas de negocio. Motivos: `INVALID_CREDENTIALS`, `ACCOUNT_DISABLED`, `UNAUTHENTICATED`, `SESSION_EXPIRED`, `FORBIDDEN`.

Las herramientas de `pruebas/` utilizan este contrato y solicitan la cuenta de cada funcionario participante. `TestAccess` asocia cada asignación con su sesión y envía LOGOUT al terminar. Ejecutarlas con `pruebas/Ejecutar.ps1`, según el [procedimiento](../pruebas/README.md). No requieren credenciales MySQL.

Con la comunicación local (127.0.0.1) las credenciales no salen del equipo. Si se usa la red, el bloque de configuración de red debe agregar un transporte protegido antes de enviar contraseñas fuera del equipo.

El rellamado usa `RECALL_TICKET;<asignación>;<ticket>` y la pantalla pública usa `GET_DISPLAY_SNAPSHOT_V2`. El detalle de esos contratos y su compatibilidad está en [RELLAMADO.md](RELLAMADO.md).
