# Gestión de turnos

Sistema de gestión de turnos en Java, organizado en cuatro proyectos Maven que se abren por separado en NetBeans.

| Proyecto | Responsabilidad |
| --- | --- |
| `CoreServer` | Entidades, reglas de atención, repositorios y servidor TCP. Catálogo, generación y ciclo de atención persistente en MySQL. |
| `CostumerTurn` | Pantalla de solicitud de turnos y confirmación, conectada al servidor. |
| `OperatorSystem` | Inicio de sesión, asignaciones propias, pausa/reanudación, ciclo de atención y consultas para ADMIN. |
| `QueueDisplayScreen` | Pantalla pública conectada al servidor, con último llamado, ventanilla, llamados recientes, turnos en espera y reloj. |

## Estado actual

La lógica de negocio se encuentra en `CoreServer`. Cada cliente contiene su pantalla y controlador. Los clientes se comunican con el servidor; las conexiones a MySQL se realizan desde `CoreServer`.

`CoreServer` escucha conexiones TCP locales en el puerto 5000 y procesa las solicitudes de catálogo, generación, asignaciones, llamado, inicio, finalización, consulta del turno activo, consulta de la pantalla pública y suscripción a sus avisos de cambios. `CostumerTurn` ejecuta sus solicitudes en segundo plano para mantener la interfaz disponible y muestra la confirmación del turno generado.

El catálogo se consulta mediante `JdbcServiceRepository`. La generación utiliza `JdbcTicketCreationRepository`, que comprueba el servicio activo, bloquea la fila de su consecutivo y guarda el turno y el nuevo consecutivo dentro de una misma transacción. Los turnos se registran como `EN_ESPERA`, con su prioridad y fecha de creación.

El llamado utiliza `JdbcTicketCallingRepository`. Dentro de una transacción vuelve a comprobar la asignación abierta e iniciada, el operador y la ventanilla activos, los servicios compatibles y la ocupación. Selecciona preferenciales primero y ordena cada prioridad por fecha de creación e identificador. Las ventanillas `PRIORITY_ONLY` atienden únicamente preferenciales. Guarda la asignación, el estado `LLAMADO` y la fecha de llamado.

El inicio y la finalización utilizan `TicketLifecycleService` y `JdbcTicketLifecycleRepository`. Cada solicitud identifica tanto la asignación como el turno interno. La transacción bloquea ambos registros, comprueba su correspondencia y el estado requerido, restaura las fechas guardadas con `Ticket.restoreActive` y aplica las reglas del modelo. El inicio guarda `EN_ATENCION` y `started_at`; la finalización guarda `FINALIZADO` y `ended_at`. El turno conserva su historial y deja de ocupar la asignación al finalizar.

Las interfaces separan los contratos de creación (`TicketCreationRepository`), llamado (`TicketCallingRepository`), inicio y finalización (`TicketLifecycleRepository`) y consulta del turno activo (`ActiveTicketRepository`). Las implementaciones en memoria se conservan como alternativa para desarrollo y comprobaciones; las operaciones conectadas de generación y atención utilizan repositorios JDBC.

`OperatorSystem` carga las asignaciones activas desde el servidor. Al seleccionar una, consulta su turno actual mediante `JdbcActiveTicketRepository` y muestra sus datos y estado. Habilita el llamado si está libre, el inicio si está `LLAMADO` y la finalización si está `EN_ATENCION`. Las acciones permanecen deshabilitadas mientras se procesa una solicitud o no se conoce el estado. Las operaciones se ejecutan en segundo plano y vuelven a consultar el turno activo al completarse. Si se pierde una confirmación, el cliente consulta el estado guardado sin repetir automáticamente la operación. Una finalización sin confirmación requiere revisar el historial; la ausencia de un turno activo por sí sola no se presenta como confirmación de esa operación. Al volver a abrir la pantalla y seleccionar la asignación, recupera el turno desde MySQL.

`QueueDisplayScreen` consulta el comando `GET_DISPLAY_SNAPSHOT` mediante `DisplayServerClient`. En CoreServer, `JdbcDisplayRepository` obtiene hasta 50 turnos en espera y 5 llamados recientes en una misma transacción de lectura con aislamiento `REPEATABLE_READ`. Los turnos en espera se ordenan por prioridad, fecha de creación e identificador; los recientes, por fecha de llamado e identificador descendentes. La consulta conserva los llamados de turnos ya finalizados.

`DisplayEventHub` distribuye avisos a las pantallas suscritas. CoreServer publica un aviso después de confirmar una generación, llamado, inicio o finalización; las consultas, los rechazos y los llamados sin turno disponible no generan avisos. Cada suscripción mantiene como máximo un aviso pendiente dentro del centro de eventos. El envío por sockets se ejecuta en el hilo de cada suscriptor, separado de la operación que modifica los datos.

`DisplayServerClient` abre un canal mediante `SUBSCRIBE_DISPLAY`. El servidor confirma con `OK;SUBSCRIBED`, envía `EVENT;DISPLAY_CHANGED` cuando hay cambios y `OK;HEARTBEAT` después de diez segundos sin avisos. `DisplayRefreshService` se suscribe antes de consultar la captura inicial para conservar los avisos que lleguen durante la sincronización. Después consulta los datos al recibir avisos, sin consultar MySQL por cada latido. Los avisos corresponden a operaciones procesadas por CoreServer; las modificaciones directas en la base no publican eventos.

La escucha y las consultas se ejecutan sobre un hilo virtual. El controlador actualiza JavaFX mediante `Platform.runLater` y mantiene el reloj con `Timeline`. Si falla la conexión o una consulta, conserva los datos visibles, muestra que están desactualizados y vuelve a intentar la suscripción después de dos segundos. Al reconectar obtiene una captura completa. Al cerrar la pantalla detiene el reloj, cierra el canal e interrumpe la escucha. La hora de última actualización permanece fija mientras no se reciben nuevos datos; el reloj sigue avanzando.

OperatorSystem exige sesión: cada funcionario atiende únicamente sus asignaciones; ADMIN dispone además de las cinco consultas. La pausa de una ventanilla impide llamar turnos nuevos y permite completar el que ya está llamado o en atención. No cierra la asignación ni elimina el historial.

## Configuración y ejecución

- Utilizar JDK 21 o superior y abrir cada carpeta de proyecto en NetBeans.
- Los tres clientes utilizan JavaFX 13, con sus FXML ajustados a la misma versión. Java y JavaFX tienen versiones independientes.
- Disponer de la estructura MySQL del proyecto y de los servicios y filas de `ticket_sequence` inicializados. La estructura está en `database/script borrador.sql`.
- Para una instalación nueva, ejecutar también `database/datos_prueba.sql`. Para una base existente, revisar las migraciones sin volver a ejecutar el esquema ni los datos iniciales.
- Inicializar las credenciales y revisar los permisos siguiendo [database/INICIALIZAR_ACCESO.md](database/INICIALIZAR_ACCESO.md). No hay contraseñas predeterminadas.
- Crear localmente `CoreServer/src/main/resources/db.properties` con las propiedades indicadas debajo. Este archivo está excluido de Git.

```properties
db.url=jdbc:mysql://HOST:3306/NOMBRE_BASE?connectionTimeZone=SERVER&preserveInstants=true
db.usuario=USUARIO
db.clave=CONTRASEÑA_LOCAL
```

- Iniciar primero `CoreServer` con su clase `cr.ac.una.CoreServer.App` y mantenerlo ejecutándose.
- Ejecutar `CostumerTurn` con **Run Project**. Se conecta a `127.0.0.1:5000` y carga los servicios automáticamente.
- Si falla la carga del catálogo, comprobar el servidor y su acceso a MySQL, y utilizar **Reconectar** en el generador.
- Ejecutar `OperatorSystem` con **Run Project** e iniciar sesión con una cuenta habilitada; después seleccionar su asignación. Se conecta a `127.0.0.1:5000`, consulta su turno activo y habilita el llamado si está libre. **Actualizar asignaciones** vuelve a consultar la lista y el estado de la selección conservada.
- Para atender, utilizar **Llamar siguiente**, **Iniciar atención** y **Finalizar atención**, en ese orden. Al confirmar que la asignación quedó libre, se habilita un nuevo llamado.
- El cliente del operador espera hasta 15 segundos por una respuesta. Una espera agotada no demuestra que el llamado haya fallado; la pantalla intenta consultar el estado guardado.
- Los parámetros de zona horaria permiten convertir los `TIMESTAMP` entre la sesión MySQL y la zona de Java. La configuración es necesaria para comparar correctamente el inicio de las asignaciones.
- Ejecutar `QueueDisplayScreen` con **Run Project**. Se suscribe a los avisos de `127.0.0.1:5000`, consulta los datos iniciales y muestra su estado de conexión. Si CoreServer se detiene, conserva los datos anteriores y vuelve a suscribirse y sincronizarse cuando está disponible; no requiere presionar botones. El canal de avisos espera hasta 25 segundos por un mensaje y las consultas de datos hasta 15 segundos por una respuesta.

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
- Visualización de datos persistidos en la pantalla pública, con ventanilla, estado y fecha de llamado.
- Generación de A006 preferencial desde CostumerTurn y aparición automática antes de los regulares. Llamado desde la ventanilla 2, salida de la lista de espera y actualización a último llamado. Inicio y finalización desde OperatorSystem reflejados automáticamente en la pantalla pública.
- Detención y reinicio de CoreServer manteniendo abierta la pantalla pública: aviso de datos desactualizados, conservación de las filas y del reloj, y recuperación automática de la conexión.

- Actualización por eventos al generar preferenciales y al llamar, iniciar y finalizar A007 desde la ventanilla 2. Se observó una demora aproximada de medio segundo a un segundo; es una observación manual, no una medición de rendimiento bajo carga.
- Recuperación del canal de notificaciones al detener y reiniciar CoreServer, con conservación de los datos y del reloj.
- Dos instancias de QueueDisplayScreen recibieron el mismo turno generado y mostraron los mismos datos automáticamente.
- Comprobaciones adicionales con sockets locales y repositorios en memoria: suscripción de varias pantallas, avisos después de operaciones exitosas, ausencia de avisos ante rechazos, latidos sin consultas de datos, cambios durante la sincronización inicial y recuperación tras desconexión. Estas comprobaciones no acceden a MySQL.

La prueba coordinada del 4 de noviembre de 2026 liberó dos solicitudes de llamado con una separación de 0,326 ms. La ventanilla 2 recibió A008 y la 1 recibió A009. Las consultas posteriores mediante CoreServer y phpMyAdmin confirmaron dos turnos distintos, prioridad preferencial, estado LLAMADO y asignaciones correctas. La consulta de ventanillas con más de un turno activo devolvió cero filas. El procedimiento, la herramienta y el resultado están en [pruebas/README.md](pruebas/README.md). Quedan por ampliar los escenarios concurrentes y comprobar la recuperación ante fallos de transacción.

## Continuación

Quedan pendientes el rellamado, la administración de servicios, ventanillas y funcionarios, la gestión de habilitaciones y asignaciones, la configuración de red y las pruebas entre computadoras. Los bloques se incorporan sin alterar el historial existente.

El acceso, los permisos, la pausa y las cinco consultas ya están implementados. El esquema y los datos iniciales están incluidos; su aplicación a la base compartida debe coordinarse con el equipo.

La revisión de QA local se documenta en [pruebas/QA_2026-11-12.md](pruebas/QA_2026-11-12.md). Las tres herramientas de concurrencia ya utilizan sesiones propias por funcionario, con inicio y cierre de sesión. Su [ejecución](pruebas/README.md) y [validación](pruebas/QA_herramientas_2026-11-12.md) están documentadas.

Se mantienen pendientes la medición del rendimiento con la base remota, la revisión de las fechas anteriores al ajuste horario y la actualización de diagramas, documento técnico y presentación. Los tiempos de una base local no sustituyen mediciones en el servidor compartido.
