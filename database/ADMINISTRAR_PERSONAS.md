# Administración de personas

## Acceso y uso

Abrir CoreServer y OperatorSystem con la versión actual. Iniciar sesión con un funcionario activo de perfil **ADMIN** y pulsar **Personas**. La vista se abre en otra ventana; al cerrar sesión se cierra también esa vista y se cancela su trabajo pendiente.

La administración no crea un administrador inicial ni contiene contraseñas predeterminadas. Si todavía no existe una cuenta administrativa con credencial, preparar su acceso siguiendo [INICIALIZAR_ACCESO.md](INICIALIZAR_ACCESO.md). No convertir a un operador en ADMIN mediante un comando público.

Esta versión usa las tablas actuales `operator`, `operator_credential` y `app_user`: **no requiere una migración adicional**. Sobre la base del equipo no se deben repetir el DDL, las migraciones 001/002/003 ni los datos de prueba.

### Funcionarios

- **Nuevo funcionario:** completar nombre, nombre de acceso, perfil y contraseña inicial. Se crea activo, sin asignaciones ni servicios habilitados automáticamente. La cuenta y su credencial se guardan en una misma transacción.
- **Editar:** seleccionar una fila, cambiar los datos y guardar. El identificador se conserva. El formulario muestra si tiene una asignación abierta y si dispone de credencial.
- **Baja/reactivación:** editar la casilla Activo. No se borra la fila ni el historial. Para cambiar el estado o perfil debe cerrarse primero cualquier asignación abierta; el cierre se realiza desde [Asignaciones](CONFIGURAR_ATENCION.md), finalizando antes cualquier turno activo y reanudando la ventanilla si estaba pausada.
- **Restablecer contraseña:** seleccionar la fila, ingresar la nueva contraseña y utilizar el botón específico. Guardar cambios no modifica silenciosamente una contraseña ingresada.

La contraseña admite de 8 a 128 caracteres según el componente de hash existente. Las nuevas cuentas y cambios de perfil admiten ADMIN u OPERADOR. SUPERVISOR permanece en el esquema por compatibilidad, sin añadirle acceso ni nuevas funciones.

No se permite desactivar o degradar al último ADMIN activo con credencial. Una cuenta ADMIN sin credencial no cuenta como alternativa de acceso. Las escrituras administrativas se serializan bloqueando los registros ADMIN en un orden común, antes de volver a validar al actor y modificar al destinatario.

Los cambios de nombre, acceso, perfil, estado o contraseña invalidan las sesiones que usaron la versión reemplazada. Una revocación atrasada no debe cerrar una sesión creada con una versión posterior. Al guardar los datos o la contraseña de la cuenta propia, la interfaz vuelve a pedir acceso para refrescar su contexto.

### Usuarios de turnos

La segunda pestaña gestiona `app_user`. Son registros relacionados con los tickets, **no cuentas de acceso del personal**. No se agrega un registro obligatorio de clientes para generar turnos.

Se permiten alta, listado, edición y baja/reactivación lógica. Se conservan identificadores y referencias `ticket.user_id`. La baja se rechaza si hay tickets en espera, llamados o en atención. Los nombres duplicados se rechazan desde el recorrido administrativo, respetando la comparación de MySQL; no se añadió un índice ni se depuraron posibles datos anteriores.

La interfaz de generación todavía no asigna usuarios a tickets. La administración no inventa esa asociación ni modifica turnos anónimos existentes.

## Seguridad, concurrencia y errores

Cada comando lleva sesión y se revalida contra la cuenta vigente, sin esperar el intervalo normal de caché. Dentro de la transacción se vuelve a comprobar que el actor siga activo, sea ADMIN y conserve la credencial con la que se autenticó. Nunca se reciben el identificador ni el rol del administrador como autoridad indicada por el cliente.

Los listados muestran un indicador de credencial, nunca contraseñas o hashes. Los comandos y respuestas usan la codificación UTF-8 existente; los registros de tiempos no incluyen parámetros sensibles.

Las operaciones JDBC son transaccionales y los accesos de red se ejecutan en segundo plano. Los controles se bloquean durante una solicitud. Si se pierde una confirmación de escritura, no se reenvía: se exige actualizar antes de seguir editando. Para un restablecimiento incierto, comprobar el acceso antes de repetirlo; el listado no revela contraseñas.

La revocación actúa sobre este proceso de CoreServer. No se implementó una coordinación distribuida de sesiones entre varios servidores centrales. Las comprobaciones de cambios concurrentes cubren los recorridos del sistema; SQL externo no participa automáticamente en sus protocolos ni avisos.

El bloque de [habilitaciones y asignaciones](CONFIGURAR_ATENCION.md) coordina la apertura/cierre con estas bajas y cambios de perfil. No invertir el orden asignación/operador del llamado ni omitir la comprobación del estado dentro de la transacción.

## Contratos de red

Todas las solicitudes llevan el prefijo `AUTH;<token>;`. Los textos indicados se codifican como el resto del protocolo. Los booleanos admiten únicamente `true` o `false` y los identificadores son enteros positivos.

| Solicitud después de AUTH | Respuesta correcta |
| --- | --- |
| `ADMIN_PEOPLE_LIST_OPERATORS` | `OK;ADMIN_PEOPLE_OPERATORS;n;...` |
| `ADMIN_PEOPLE_CREATE_OPERATOR;nombre;acceso;rol;contraseña` | `OK;ADMIN_PEOPLE_OPERATOR_CREATED;id` |
| `ADMIN_PEOPLE_UPDATE_OPERATOR;id;nombre;acceso;rol;activo` | `OK;ADMIN_PEOPLE_OPERATOR_UPDATED;id` |
| `ADMIN_PEOPLE_RESET_PASSWORD;id;contraseña` | `OK;ADMIN_PEOPLE_PASSWORD_RESET;id` |
| `ADMIN_PEOPLE_LIST_USERS` | `OK;ADMIN_PEOPLE_USERS;n;...` |
| `ADMIN_PEOPLE_CREATE_USER;usuario` | `OK;ADMIN_PEOPLE_USER_CREATED;id` |
| `ADMIN_PEOPLE_UPDATE_USER;id;usuario;activo` | `OK;ADMIN_PEOPLE_USER_UPDATED;id` |

Cada funcionario del listado contiene siete campos: `id;nombre;acceso;rol;activo;tiene_credencial;tiene_asignacion_abierta`. Cada usuario contiene tres: `id;usuario;activo`. Se conserva el formato de rechazo `DENIED;motivo;mensaje` y de negocio `ERROR;mensaje`.

## Ubicación del código

- CoreServer: `PeopleAdministrationService`, `PeopleAdministrationRepository`, `JdbcPeopleAdministrationRepository` y `PeopleAdministrationHandler` separan reglas de entrada, contratos, SQL y protocolo.
- `AdministrationActor` identifica internamente al administrador y la credencial autenticada. `AccountChange` identifica la versión anterior y posterior para revocar sesiones sin afectar accesos más recientes. No son DTO públicos.
- `ManagedOperator` y `ManagedAppUser` son los datos de los listados, sin secretos.
- OperatorSystem: `PeopleServerClient`, el contrato `PeopleRequests`, `PeopleController` y `people.fxml`. La vista reutiliza `operator.css`.

Ver [el informe de pruebas](../pruebas/QA_personas_2026-11-12.md). El bloque está validado sobre MySQL local desechable; su recorrido administrativo no se ejecutó contra la base compartida durante esta implementación.
