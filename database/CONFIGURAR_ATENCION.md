# Habilitaciones y asignaciones de atención

## Acceso

Iniciar CoreServer y OperatorSystem actualizados, entrar con una cuenta **ADMIN** y pulsar **Asignaciones**. La vista contiene **Servicios habilitados** y **Asignaciones e historial**. Conserva el diseño y los permisos de la aplicación; los clientes no acceden directamente a MySQL.

El bloque utiliza `operator_service`, `window_service` y `operator_window_assignment` existentes. **No requiere tablas ni migraciones nuevas.** No repetir el esquema, las migraciones 001/002/003 ni los datos iniciales sobre la base compartida.

La vista trabaja con servicios y ventanillas existentes, incluidos los inactivos, para preparar sus habilitaciones y consultar su historia. Su creación y edición todavía no están integradas.

## Configurar servicios

1. Elegir Funcionario o Ventanilla y seleccionar el registro.
2. Seleccionar un servicio de la tabla. Se muestran su estado y si está habilitado.
3. Pulsar Habilitar servicio o Retirar habilitación y confirmar.

Se pueden preparar habilitaciones de un funcionario o una ventanilla inactivos, pero solo con servicios activos. Eso no activa al destinatario ni abre una asignación. La atención efectiva sigue siendo la intersección de servicios activos habilitados para ambos.

La retirada se rechaza si el servicio tiene turnos en espera, si corresponde a un turno llamado/en atención del destinatario o si deja una asignación abierta sin ningún servicio activo compartido. Estas restricciones conservan la compatibilidad de las asignaciones y los turnos existentes. No se transfieren ni cancelan tickets para forzar el cambio.

Repetir una habilitación existente o retirar una que no existe no duplica enlaces ni crea registros adicionales. Las bajas no borran turnos históricos. Esta configuración define servicios permitidos, no una reserva de capacidad ni una garantía de que siempre habrá personal disponible para toda emisión futura.

## Abrir una asignación

1. En Asignaciones e historial, seleccionar funcionario y ventanilla.
2. Verificar que el funcionario esté activo, tenga credencial y perfil ADMIN u OPERADOR; la ventanilla debe estar ACTIVA, sin pausa.
3. Ambos deben compartir al menos un servicio activo y no tener otra asignación abierta.
4. Pulsar Abrir asignación y confirmar. Se crea una fila nueva, con inicio inmediato; no se reciclan filas cerradas ni se programan horarios futuros.
5. El funcionario utiliza Actualizar asignaciones en su pantalla de atención para recuperar la nueva selección.

Una ventanilla PRIORITY_ONLY conserva su exclusividad preferencial. No se cambia la política de prioridad ni se asignan servicios automáticamente.

## Cerrar una asignación

Seleccionar su fila en el historial y pulsar Cerrar asignación seleccionada. Si tiene un turno LLAMADO o EN_ATENCION se rechaza: debe finalizarse primero. Si la ventanilla está PAUSADA, su operador debe reanudarla antes del cierre para no dejarla pausada sin una asignación utilizable.

El cierre guarda `ended_at`; conserva identificador, funcionario, ventanilla y tickets asociados. Las fechas de asignación mantienen la precisión de segundos del esquema actual. Una nueva ocupación genera otra fila. Los reportes atribuyen las atenciones a la asignación histórica del turno, no al funcionario que ocupa ahora esa ventanilla.

La lista administrativa permite consultar abiertas y cerradas. Abrir o cerrar no cambia automáticamente el estado administrativo de la ventanilla. El funcionario puede refrescar su lista mediante Actualizar asignaciones. Esta actualización administrativa manual no sustituye ni altera las notificaciones automáticas de la pantalla pública.

## Concurrencia y permisos

Las solicitudes exigen ADMIN y revalidación de sesión. Se comparte la comprobación JDBC del actor con la administración de personas, sin exponer credenciales. Las escrituras vuelven a comprobar al actor dentro de su transacción; una comprobación de interfaz no basta.

`RoutingConfigurationLock` obtiene un bloqueo nombrado de MySQL por base mediante GET_LOCK, con espera máxima de cinco segundos. Serializa entre sí las modificaciones de habilitaciones y asignaciones y se libera al salir, incluso después de un rollback. No crea una tabla ni reemplaza restricciones SQL. Si no puede obtenerlo, se informa que hay otro cambio en curso; no hay un bucle automático de reintentos.

El llamado normal no toma ese bloqueo administrativo: sigue usando sus filas y transacciones. El cierre bloquea asignación, funcionario y ventanilla, compatible con el orden del llamado. La apertura todavía no tiene fila propia: bloquea funcionario y ventanilla y comprueba la ocupación; los índices únicos existentes constituyen otra defensa frente a duplicados.

Las habilitaciones bloquean al destinatario y después el servicio, sin adquirir a continuación el bloqueo de la asignación. Así evitan el orden inverso al llamado. Se usa READ_COMMITTED en las escrituras para que las comprobaciones posteriores a una espera no lean una captura antigua. La compatibilidad toma FOR SHARE para mantener el servicio válido hasta confirmar. La captura administrativa completa usa una transacción de lectura REPEATABLE_READ.

Cualquier implementación futura de servicios y ventanillas que retire rutas, dé de baja registros o cambie modalidades debe coordinar sus validaciones y bloqueos con estos recorridos. No invertir el orden de filas ni asumir que una comprobación fuera de la transacción evita una carrera. El bloqueo nombrado es común por base y puede reutilizarse al concretar esas modificaciones; no tomarlo después de bloquear filas que otro recorrido espera con él adquirido.

La serialización es conservadora y no constituye una medición de escalabilidad. Las modificaciones SQL externas no participan en este recorrido de aplicación. No se prometen cancelaciones de operaciones ya en curso al revocar una sesión.

## Protocolo

Las solicitudes llevan `AUTH;<token>;` y los textos usan la codificación UTF-8 existente.

| Solicitud después de AUTH | Respuesta correcta |
| --- | --- |
| `ADMIN_ROUTING_SNAPSHOT` | `OK;ADMIN_ROUTING_SNAPSHOT;no;nw;ns;nos;nws;na;...` |
| `ADMIN_ROUTING_OPERATOR_SERVICE;id;servicio;habilitado` | `OK;ADMIN_ROUTING_OPERATOR_SERVICE;id;servicio;habilitado` |
| `ADMIN_ROUTING_WINDOW_SERVICE;id;servicio;habilitado` | `OK;ADMIN_ROUTING_WINDOW_SERVICE;id;servicio;habilitado` |
| `ADMIN_ROUTING_OPEN_ASSIGNMENT;funcionario;ventanilla` | `OK;ADMIN_ROUTING_ASSIGNMENT_OPENED;id` |
| `ADMIN_ROUTING_CLOSE_ASSIGNMENT;id` | `OK;ADMIN_ROUTING_ASSIGNMENT_CLOSED;id` |

Los seis contadores de la captura corresponden a funcionarios, ventanillas, servicios, habilitaciones del funcionario, habilitaciones de ventanilla y asignaciones. Cada fila contiene, en ese mismo orden:

- Funcionario: `id;nombre;rol;activo;tiene_credencial`.
- Ventanilla: `id;estado;modalidad`.
- Servicio: `codigo;nombre;activo`.
- Habilitación: `id_destinatario;codigo_servicio`.
- Asignación: `id;id_funcionario;id_ventanilla;inicio;fin`, con fin vacío mientras esté abierta.

El cliente valida cantidades, campos, identificadores, estados y fechas. Una confirmación inválida o perdida no provoca reenvío: bloquea la edición hasta consultar de nuevo. Comprobar la habilitación o la fila histórica antes de repetir una acción.

Los errores conservan `DENIED;motivo;mensaje` y `ERROR;mensaje`. No se modifican los contratos de llamado, reportes, Personas ni snapshots públicos V1/V2. Se conservan los constructores anteriores de TurnRequestHandler para las herramientas existentes.

## Código y verificación

CoreServer incorpora `AssignmentConfigurationService`, `AssignmentConfigurationRepository`, `JdbcAssignmentConfigurationRepository` y `AssignmentConfigurationHandler`. `AssignmentConfiguration` representa la captura. OperatorSystem utiliza `AssignmentServerClient`, `AssignmentRequests`, `AssignmentsController` y `assignments.fxml`.

Las habilitaciones y asignaciones se comprobaron sobre MySQL local desechable. Esa administración no se ejecutó contra la base compartida durante las pruebas. Consultar el [alcance de las comprobaciones](../pruebas/README.md#comprobaciones-y-límites).
