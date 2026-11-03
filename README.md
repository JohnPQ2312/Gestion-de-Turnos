# Gestión de turnos

Sistema de gestión de turnos en Java, organizado en cuatro proyectos Maven que se abren por separado en NetBeans.

| Proyecto | Responsabilidad |
| --- | --- |
| `CoreServer` | Entidades, reglas de atención, repositorios y servidor TCP. Catálogo, generación y ciclo de atención persistente en MySQL. |
| `CostumerTurn` | Pantalla de solicitud de turnos y confirmación, conectada al servidor. |
| `OperatorSystem` | Consulta de asignaciones, llamado, inicio, finalización y recuperación del turno actual mediante el servidor. |
| `QueueDisplayScreen` | Pantalla pública de turnos; conexión pendiente. |

## Estado actual

La lógica de negocio se encuentra en `CoreServer`. Cada cliente contiene su pantalla y controlador. Los clientes se comunican con el servidor; las conexiones a MySQL se realizan desde `CoreServer`.

`CoreServer` escucha conexiones TCP locales en el puerto 5000 y procesa las solicitudes de catálogo, generación, asignaciones, llamado, inicio, finalización y consulta del turno activo. `CostumerTurn` ejecuta sus solicitudes en segundo plano para mantener la interfaz disponible y muestra la confirmación del turno generado.

El catálogo se consulta mediante `JdbcServiceRepository`. La generación utiliza `JdbcTicketCreationRepository`, que comprueba el servicio activo, bloquea la fila de su consecutivo y guarda el turno y el nuevo consecutivo dentro de una misma transacción. Los turnos se registran como `EN_ESPERA`, con su prioridad y fecha de creación.

El llamado utiliza `JdbcTicketCallingRepository`. Dentro de una transacción vuelve a comprobar la asignación abierta e iniciada, el operador y la ventanilla activos, los servicios compatibles y la ocupación. Selecciona preferenciales primero y ordena cada prioridad por fecha de creación e identificador. Las ventanillas `PRIORITY_ONLY` atienden únicamente preferenciales. Guarda la asignación, el estado `LLAMADO` y la fecha de llamado.

El inicio y la finalización utilizan `TicketLifecycleService` y `JdbcTicketLifecycleRepository`. Cada solicitud identifica tanto la asignación como el turno interno. La transacción bloquea ambos registros, comprueba su correspondencia y el estado requerido, restaura las fechas guardadas con `Ticket.restoreActive` y aplica las reglas del modelo. El inicio guarda `EN_ATENCION` y `started_at`; la finalización guarda `FINALIZADO` y `ended_at`. El turno conserva su historial y deja de ocupar la asignación al finalizar.

Las interfaces separan los contratos de creación (`TicketCreationRepository`), llamado (`TicketCallingRepository`), inicio y finalización (`TicketLifecycleRepository`) y consulta del turno activo (`ActiveTicketRepository`). Las implementaciones en memoria se conservan como alternativa para desarrollo y comprobaciones; las operaciones conectadas de generación y atención utilizan repositorios JDBC.

`OperatorSystem` carga las asignaciones activas desde el servidor. Al seleccionar una, consulta su turno actual mediante `JdbcActiveTicketRepository` y muestra sus datos y estado. Habilita el llamado si está libre, el inicio si está `LLAMADO` y la finalización si está `EN_ATENCION`. Las acciones permanecen deshabilitadas mientras se procesa una solicitud o no se conoce el estado. Las operaciones se ejecutan en segundo plano y vuelven a consultar el turno activo al completarse. Si se pierde una confirmación, el cliente consulta el estado guardado sin repetir automáticamente la operación. Una finalización sin confirmación requiere revisar el historial; la ausencia de un turno activo por sí sola no se presenta como confirmación de esa operación. Al volver a abrir la pantalla y seleccionar la asignación, recupera el turno desde MySQL.

`QueueDisplayScreen` conserva su pantalla, pendiente de conexión al servidor, y abre sin datos.

## Configuración y ejecución

- Utilizar JDK 21 o superior y abrir cada carpeta de proyecto en NetBeans.
- Los tres clientes utilizan JavaFX 13, con sus FXML ajustados a la misma versión. Java y JavaFX tienen versiones independientes.
- Disponer de la estructura MySQL del proyecto y de los servicios y filas de `ticket_sequence` inicializados. La estructura está en `database/script borrador.sql`.
- Crear localmente `CoreServer/src/main/resources/db.properties` con las propiedades indicadas debajo. Este archivo está excluido de Git.

```properties
db.url=jdbc:mysql://HOST:3306/NOMBRE_BASE?connectionTimeZone=SERVER&preserveInstants=true
db.usuario=USUARIO
db.clave=CONTRASEÑA_LOCAL
```

- Iniciar primero `CoreServer` con su clase `cr.ac.una.CoreServer.App` y mantenerlo ejecutándose.
- Ejecutar `CostumerTurn` con **Run Project**. Se conecta a `127.0.0.1:5000` y carga los servicios automáticamente.
- Si falla la carga del catálogo, comprobar el servidor y su acceso a MySQL, y utilizar **Reconectar** en el generador.
- Ejecutar `OperatorSystem` con **Run Project** y seleccionar una asignación. Se conecta a `127.0.0.1:5000`, consulta su turno activo y habilita el llamado si está libre. **Actualizar asignaciones** vuelve a consultar la lista y el estado de la selección conservada.
- Para atender, utilizar **Llamar siguiente**, **Iniciar atención** y **Finalizar atención**, en ese orden. Al confirmar que la asignación quedó libre, se habilita un nuevo llamado.
- El cliente del operador espera hasta 15 segundos por una respuesta. Una espera agotada no demuestra que el llamado haya fallado; la pantalla intenta consultar el estado guardado.
- Los parámetros de zona horaria permiten convertir los `TIMESTAMP` entre la sesión MySQL y la zona de Java. La configuración es necesaria para comparar correctamente el inicio de las asignaciones.
- `QueueDisplayScreen` puede abrirse para visualizar su pantalla; su comunicación está pendiente.

## Comprobaciones realizadas

- Registro de un turno regular y consulta de su fila y consecutivo en MySQL.
- Continuación de la numeración después de reiniciar `CoreServer`.
- Registro de un turno preferencial y comprobación de su prioridad en MySQL.
- Consecutivos independientes por servicio: Información general y Trámites.
- Generación desde dos ejecuciones de `CostumerTurn` conectadas al mismo servidor, con códigos distintos.
- Carga de asignaciones activas desde MySQL después de configurar la conversión horaria.
- Llamado preferencial de A003 desde la asignación 2 y comprobación de su estado `LLAMADO`, asignación y fecha en MySQL.
- Confirmación de un llamado desde la ventanilla mixta y rechazo de otro mientras la asignación sigue ocupada.
- Recuperación de A003 en la pantalla del operador tras perder su confirmación, con el botón de llamado deshabilitado.
- Inicio y finalización de A003 desde OperatorSystem. La consulta posterior en MySQL mostró `FINALIZADO`, la asignación 2 y las fechas de creación, llamado, inicio y fin. Entre el inicio y el fin transcurrieron 27 segundos.
- Tras finalizar A003, la ventanilla preferencial quedó libre y respondió que no había turnos compatibles, sin tomar los regulares pendientes.
- Comprobaciones del protocolo con repositorios en memoria y del FXML y tareas de interfaz con respuestas simuladas, incluida la recuperación tras una confirmación perdida.
- Comprobaciones adicionales del ciclo con JDBC simulado y de la interfaz con respuestas simuladas: estados permitidos, identificadores incorrectos, conservación de fechas, reversión ante fallos, bloqueo de controles y recuperación de un inicio con confirmación perdida. Estas simulaciones no sustituyen las pruebas de concurrencia ni de fallos contra MySQL real.

La prueba manual con dos clientes no demuestra por sí sola el comportamiento bajo solicitudes exactamente simultáneas. La comprobación automatizada de concurrencia del repositorio JDBC y la de recuperación ante fallos de transacción siguen pendientes.

## Continuación

Conectar `QueueDisplayScreen` al servidor para mostrar turnos, ventanillas, llamados recientes y reloj, con actualización automática.

Para completar el proyecto también faltan la autenticación y los permisos, las funciones administrativas, la pausa y el rellamado del operador, los reportes y la validación integral con varios clientes y operadores independientes.

También quedan pendientes la medición y optimización de la duración del llamado y la revisión de las fechas de los datos de prueba grabados antes del ajuste horario, antes de utilizarlos para validar métricas de espera.
