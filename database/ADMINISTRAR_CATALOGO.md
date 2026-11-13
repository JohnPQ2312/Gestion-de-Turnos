# Administración de servicios y ventanillas

## Acceso

Iniciar CoreServer y OperatorSystem actualizados, entrar con una cuenta **ADMIN** y pulsar **Servicios y ventanillas**. La ventana tiene dos pestañas, **Servicios** y **Ventanillas**, cada una con listado, formulario, **Actualizar** y mensajes propios. Al cerrar sesión se cierra la ventana y se cancela su trabajo pendiente.

La administración utiliza `service`, `ticket_sequence` y `window` del DDL actual, sin tablas ni migraciones nuevas. No repetir el esquema, las migraciones 001/002/003 ni los datos iniciales sobre la base compartida.

El alta de un servicio o una ventanilla conserva separada la configuración de sus habilitaciones y asignaciones. Esa configuración sigue en [Asignaciones](CONFIGURAR_ATENCION.md).

## Servicios

- Alta: código, nombre y prefijo. El código es la identidad estable (empieza con letra; letras mayúsculas, dígitos o `_`; hasta 20). El prefijo nuevo admite de 1 a 5 letras ASCII mayúsculas, sin dígitos, para que `prefijo + número` no sea ambiguo. La interfaz convierte a mayúsculas. El servicio y su fila de `ticket_sequence` en 0 se crean en la misma transacción. Queda **ACTIVO**, porque Asignaciones solo habilita servicios activos.
- Edición: nombre y prefijo. El código no cambia. El prefijo solo se puede cambiar si el servicio nunca emitió turnos (consecutivo en 0 y sin tickets). Se rechazan los nombres duplicados (según la comparación de MySQL), los prefijos ocupados y los prefijos que coincidan con códigos históricos de otro servicio. Un prefijo anterior que no siga el formato nuevo puede conservarse al editar solo el nombre.
- Desactivar: se rechaza si hay turnos `EN_ESPERA`, `LLAMADO` o `EN_ATENCION` del servicio, con el conteo de cada estado. También se rechaza si alguna asignación abierta quedaría sin servicios activos en común, la misma política de Asignaciones. No se transfieren ni cancelan turnos. Las habilitaciones se conservan; mientras el servicio está inactivo no cuentan para el llamado.
- Reactivar: conserva la numeración. Si faltara la fila de consecutivo (datos anteriores), se crea a partir del mayor consecutivo usado; si no hay tickets históricos, comienza en 0.
- El listado muestra turnos emitidos, pendientes y **rutas abiertas** (asignaciones abiertas cuyo funcionario y ventanilla tienen habilitado el servicio). Un servicio activo sin rutas se señala, porque sus turnos quedarían en espera.

CostumerTurn consulta los servicios activos mediante `LIST_SERVICES`. Su botón **Actualizar servicios** permite recuperar los cambios del catálogo. Al renombrar un servicio se emite `DISPLAY_CHANGED` para que las pantallas vuelvan a consultar el nombre.

## Ventanillas

- Alta: número público (1 a 999, identificador estable) y modalidad `MIXED` o `PRIORITY_ONLY`. Se crea **ACTIVA**, sin servicios ni asignación.
- Edición: solo la modalidad. Se rechaza si la ventanilla tiene un turno `LLAMADO` o `EN_ATENCION`. El llamado ya compara la modalidad dentro de su transacción, así que un operador con datos anteriores recibe «La modalidad de la ventanilla cambió» y debe usar **Actualizar asignaciones**.
- Dar de baja (`INACTIVA`): se rechaza si tiene una asignación abierta, e indica cuál y con qué funcionario; se cierra primero desde Asignaciones. No se cierran ni borran asignaciones de forma implícita. Se conservan el historial y `window_service`.
- Reactivar: solo desde `INACTIVA`. `PAUSADA` no es una baja: la reanuda su operador desde Atención con el flujo de pausa existente. La administración no pausa ni reanuda.

## Concurrencia y permisos

Cada comando lleva sesión y se resuelve con `AuthService.administrationActor`. Dentro de cada transacción se revalida al ADMIN con `JdbcAdministrationAccess`. Ocultar el botón no sustituye esa comprobación.

Las escrituras toman primero el bloqueo nombrado de `RoutingConfigurationLock`. Así se serializan con las habilitaciones, las asignaciones y entre sí. Después bloquean la fila del servicio o de la ventanilla y hacen sus comprobaciones con lecturas sin bloqueo, en READ_COMMITTED:

- La emisión bloquea servicio y luego consecutivo; el cambio de prefijo usa ese mismo orden. Con el servicio bloqueado no se emiten ni llaman turnos nuevos suyos, así que el conteo de pendientes es estable hasta confirmar.
- El llamado bloquea primero la asignación, después el funcionario, la ventanilla y el servicio. El catálogo mantiene su fila bloqueada mientras realiza lecturas de control, sin adquirir después las filas que el llamado ya tiene. Un llamado concurrente espera y vuelve a comprobar el estado y la modalidad.
- La apertura y el cierre de asignaciones toman el mismo bloqueo nombrado, de modo que una baja de ventanilla no se cruza con una apertura.

Los listados usan una transacción de solo lectura REPEATABLE_READ. Si otra operación mantiene ocupado el bloqueo nombrado durante el plazo permitido, se informa que hay otro cambio de configuración administrativa en curso.

## Protocolo

Todas las solicitudes llevan `AUTH;<token>;`. Los textos usan la codificación UTF-8 existente.

| Solicitud después de AUTH | Respuesta correcta |
| --- | --- |
| `ADMIN_SERVICE_LIST` | `OK;ADMIN_SERVICES;n;...` |
| `ADMIN_SERVICE_CREATE;código;nombre;prefijo` | `OK;ADMIN_SERVICE_CREATED;código` |
| `ADMIN_SERVICE_UPDATE;código;nombre;prefijo` | `OK;ADMIN_SERVICE_UPDATED;código` |
| `ADMIN_SERVICE_SET_ACTIVE;código;true\|false` | `OK;ADMIN_SERVICE_STATE;código;activo` |
| `ADMIN_WINDOW_LIST` | `OK;ADMIN_WINDOWS;n;...` |
| `ADMIN_WINDOW_CREATE;id;modalidad` | `OK;ADMIN_WINDOW_CREATED;id` |
| `ADMIN_WINDOW_UPDATE_MODE;id;modalidad` | `OK;ADMIN_WINDOW_UPDATED;id;modalidad` |
| `ADMIN_WINDOW_SET_ACTIVE;id;true\|false` | `OK;ADMIN_WINDOW_STATE;id;ACTIVA\|INACTIVA` |

- Servicio, 8 campos: `código;nombre;prefijo;activo;emitidos;pendientes;rutas_abiertas;prefijo_bloqueado`.
- Ventanilla, 7 campos: `id;estado;modalidad;asignación_abierta;funcionario;turno_activo;servicios_activos`. La asignación vale 0 y el funcionario va vacío si no hay asignación abierta.

Se conservan `DENIED;motivo;mensaje` y `ERROR;mensaje`. El cliente valida cantidades, campos, estados y que la confirmación corresponda a lo solicitado. Una confirmación perdida o inválida no se reenvía: la pestaña queda bloqueada hasta pulsar **Actualizar**. No se modificaron los contratos de atención, reportes, Personas, Asignaciones ni los snapshots V1/V2. Se conservan los constructores anteriores de `TurnRequestHandler`.

## Código

- CoreServer: `CatalogAdministrationService`, `CatalogAdministrationRepository`, `JdbcCatalogAdministrationRepository`, `CatalogAdministrationHandler`, `ManagedService` y `ManagedWindow`. Además, conexión en `App` y `TurnRequestHandler`.
- OperatorSystem: `CatalogServerClient`, `CatalogRequests`, `CatalogController`, `ServicesController`, `WindowsController`, `AdminTaskRunner`, `ManagedService`, `ManagedWindow`, `catalog.fxml`, `services.fxml` y `windows.fxml`. Además, el botón en `operatorScreen.fxml`/`OperatorController` y la ventana en `App`.

## Comprobaciones y alcance

Los cuatro proyectos compilan con Maven. Se comprobaron las operaciones JDBC y el protocolo sobre MySQL local, y las vistas JavaFX con respuestas controladas. El [resumen de comprobaciones](../pruebas/README.md#comprobaciones-y-límites) distingue esos ensayos. El recorrido integrado del catálogo sobre la base compartida y el despliegue entre computadoras siguen pendientes.
