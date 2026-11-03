# Gestión de turnos

Sistema de gestión de turnos en Java, organizado en cuatro proyectos Maven que se abren por separado en NetBeans.

| Proyecto | Responsabilidad |
| --- | --- |
| `CoreServer` | Entidades, reglas de atención, repositorios y servidor TCP. Catálogo, generación, llamado y consulta de turnos activos con MySQL. |
| `CostumerTurn` | Pantalla de solicitud de turnos y confirmación, conectada al servidor. |
| `OperatorSystem` | Consulta de asignaciones activas, llamado y recuperación del turno actual mediante el servidor. |
| `QueueDisplayScreen` | Pantalla pública de turnos; conexión pendiente. |

## Estado actual

La lógica de negocio se encuentra en `CoreServer`. Cada cliente contiene su pantalla y controlador. Los clientes se comunican con el servidor; las conexiones a MySQL se realizan desde `CoreServer`.

`CoreServer` escucha conexiones TCP locales en el puerto 5000 y procesa las solicitudes de catálogo, generación, asignaciones, llamado y consulta del turno activo. `CostumerTurn` ejecuta sus solicitudes en segundo plano para mantener la interfaz disponible y muestra la confirmación del turno generado.

El catálogo se consulta mediante `JdbcServiceRepository`. La generación utiliza `JdbcTicketCreationRepository`, que comprueba el servicio activo, bloquea la fila de su consecutivo y guarda el turno y el nuevo consecutivo dentro de una misma transacción. Los turnos se registran como `EN_ESPERA`, con su prioridad y fecha de creación.

El llamado utiliza `JdbcTicketCallingRepository`. Dentro de una transacción vuelve a comprobar la asignación abierta e iniciada, el operador y la ventanilla activos, los servicios compatibles y la ocupación. Selecciona preferenciales primero y ordena cada prioridad por fecha de creación e identificador. Las ventanillas `PRIORITY_ONLY` atienden únicamente preferenciales. Guarda la asignación, el estado `LLAMADO` y la fecha de llamado.

Las interfaces separan los contratos de creación (`TicketCreationRepository`), llamado (`TicketCallingRepository`) y consulta del turno activo (`ActiveTicketRepository`). Las implementaciones en memoria se conservan como alternativa para desarrollo y comprobaciones; las operaciones conectadas de generación y atención utilizan repositorios JDBC.

`OperatorSystem` carga las asignaciones activas desde el servidor. Al seleccionar una, consulta su turno actual mediante `JdbcActiveTicketRepository` y muestra sus datos y estado. El llamado queda deshabilitado cuando existe un turno activo o no se ha podido comprobar la ocupación. Las solicitudes se ejecutan en segundo plano. Si se pierde una confirmación, el cliente consulta el turno guardado sin repetir automáticamente el llamado. Al volver a abrir la pantalla y seleccionar la asignación, recupera el turno desde MySQL.

`QueueDisplayScreen` conserva su pantalla, pendiente de conexión al servidor, y abre sin datos. La lógica de inicio y finalización existe en el modelo `Ticket`; su persistencia y los controles correspondientes de OperatorSystem todavía están pendientes.

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
- Comprobaciones del protocolo con repositorios en memoria y del FXML y tareas de interfaz con respuestas simuladas, incluida la recuperación tras una confirmación perdida.

La prueba manual con dos clientes no demuestra por sí sola el comportamiento bajo solicitudes exactamente simultáneas. La comprobación automatizada de concurrencia del repositorio JDBC y la de recuperación ante fallos de transacción siguen pendientes.

## Continuación

Implementar el inicio y la finalización de la atención, con sus cambios de estado y fechas en MySQL. Después, conectar las actualizaciones de `QueueDisplayScreen`.

También quedan pendientes la medición y optimización de la duración del llamado y la revisión de las fechas de los datos de prueba grabados antes del ajuste horario, antes de utilizarlos para validar métricas de espera.
