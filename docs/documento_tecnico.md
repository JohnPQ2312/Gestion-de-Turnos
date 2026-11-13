# Sistema distribuido de Gestión de Turnos

Documento técnico en elaboración. Este borrador desarrolla los apartados 3 a 7 de la estructura solicitada. Versión de referencia: `cc9405e`. La [lista de apartados y su estado](README.md) identifica el contenido restante.

## 3. Descripción del problema

Una institución que ofrece varios servicios necesita organizar a las personas que esperan atención y distribuirlas entre sus ventanillas. El sistema debe identificar el servicio solicitado, asignar un número de turno y comunicar cuándo y dónde será atendida cada persona.

La coordinación se vuelve más compleja cuando varios funcionarios trabajan simultáneamente. Si dos ventanillas solicitan el siguiente turno casi al mismo tiempo, solo una debe recibirlo. También debe evitarse que una ventanilla tenga dos turnos activos, que se asigne un servicio incompatible con el funcionario o que un turno finalizado vuelva a la cola como si fuera nuevo.

Las personas en espera necesitan ver los llamados sin depender de que alguien actualice manualmente la pantalla. Los funcionarios requieren conocer el estado de su ventanilla y del turno que están atendiendo. La administración necesita configurar las relaciones de atención y consultar posteriormente las operaciones realizadas.

La solución debe conservar los turnos, sus estados, responsables y fechas aun después de cerrar una aplicación. También debe manejar credenciales incorrectas, falta de turnos, operaciones no permitidas y pérdidas de conexión, mostrando información suficiente para que el usuario decida cómo continuar.

## 4. Descripción general de la solución

Se desarrolló un sistema cliente-servidor en Java, organizado en cuatro proyectos Maven. Tres aplicaciones JavaFX presentan las operaciones a las personas y a los funcionarios; un servidor central coordina las reglas, las sesiones, la comunicación y la persistencia en MySQL.

| Aplicación | Función |
| --- | --- |
| `CoreServer` | Recibe conexiones, procesa solicitudes, verifica permisos y reglas, ejecuta operaciones JDBC y avisa a las pantallas públicas cuando cambian los turnos. |
| `CostumerTurn` | Muestra los servicios disponibles, permite elegir atención regular o preferencial y confirma el código del turno generado. |
| `OperatorSystem` | Permite iniciar sesión, consultar asignaciones propias, llamar y rellamar, iniciar y finalizar atenciones, y pausar o reanudar la ventanilla. El perfil ADMIN dispone además de consultas y administración de personas y asignaciones. |
| `QueueDisplayScreen` | Muestra el último llamado y su ventanilla, llamados recientes, turnos en espera, reloj y estado de conexión. Se actualiza mediante avisos del servidor. |

### Recorrido de un turno

1. La persona selecciona un servicio y su tipo de atención en CostumerTurn.
2. CoreServer comprueba que el servicio esté activo y guarda el turno junto con la actualización del consecutivo de ese servicio en una misma transacción. El código combina el prefijo del servicio y el número; la prioridad se registra por separado.
3. Un funcionario autenticado solicita el siguiente turno de su asignación. El servidor comprueba la disponibilidad, la ocupación y los servicios que comparten el funcionario y la ventanilla.
4. Entre los turnos compatibles se atienden primero los preferenciales y, dentro de cada prioridad, se conserva el orden por fecha de creación e identificador. Una ventanilla exclusivamente preferencial no toma turnos regulares. Las mixtas pueden atender ambos tipos.
5. El turno pasa por `EN_ESPERA`, `LLAMADO`, `EN_ATENCION` y `FINALIZADO`. Las operaciones guardan sus fechas y mantienen la relación con la asignación que atendió a la persona.
6. Después de confirmar los cambios, el servidor avisa a las pantallas suscritas. Cada pantalla consulta una captura actualizada y la presenta automáticamente.

El rellamado conserva el mismo turno, su código y su primer llamado; actualiza el último llamado y el contador. La pausa impide nuevos llamados y permite completar la atención ya iniciada. La finalización conserva el historial y libera la ocupación de la asignación.

Las cinco consultas permiten revisar turnos atendidos entre fechas, cantidades por funcionario y por servicio, tiempos de espera y tiempos de atención. La espera se calcula desde la generación hasta el inicio de atención; la atención, desde el inicio hasta el fin.

La administración actual gestiona funcionarios, credenciales y usuarios relacionados con turnos, además de habilitaciones de servicios y apertura/cierre de asignaciones. El registro `app_user` no es una cuenta de acceso del personal; emitir un turno no exige registrar previamente a la persona.

**Alcance del corte:** la administración completa de servicios y ventanillas está pendiente de integrar. No se presenta como funcionalidad terminada. La política de prioridad es estricta y no garantiza un tiempo máximo de espera para los regulares. La atención preferencial con ventanilla exclusiva y apoyo de las mixtas es la característica elegida para desarrollar la justificación de innovación; PDF, QR y transferencias no forman parte de la implementación de este corte.

## 5. Arquitectura utilizada

La arquitectura combina la separación cliente-servidor con una organización por capas. Los cuatro proyectos se compilan por separado. Los clientes no se conectan a MySQL ni comparten una instancia Java de repositorio: intercambian mensajes con CoreServer. El servidor ensambla los servicios, los contratos de acceso a datos y sus implementaciones JDBC.

### Presentación

Las tres aplicaciones gráficas utilizan vistas FXML, estilos CSS y controladores JavaFX. Los controladores leen la entrada, coordinan solicitudes, muestran resultados y habilitan las acciones según el estado conocido. Las operaciones de entrada/salida se ejecutan fuera del hilo de la interfaz.

Por ejemplo, `TicketsGeneratorController` recibe las funciones necesarias para consultar servicios y generar turnos. Su cliente de red, `TurnServerClient`, prepara y procesa los mensajes. El controlador no calcula el consecutivo ni inserta directamente el ticket.

### Comunicación y coordinación

Los clientes de red traducen las operaciones de las pantallas al protocolo del servidor. `NetworkSettings` lee la dirección, el puerto y la configuración TLS dentro de cada aplicación. Sin configuración se permite la ejecución local en loopback; las conexiones fuera del equipo exigen TLS y validación del certificado del servidor.

En CoreServer, `TurnServer` acepta las conexiones y entrega las solicitudes a `TurnRequestHandler`. Los manejadores específicos de Personas y Asignaciones separan sus comandos administrativos. La autorización se comprueba en el servidor; la visibilidad de un botón no constituye un permiso.

La pantalla pública mantiene un canal de suscripción. `DisplayEventHub` distribuye avisos de cambios y `DisplayRefreshService` coordina la consulta de datos y la recuperación de la conexión. Los avisos no contienen una copia completa de la base: indican que debe consultarse su estado actualizado.

### Lógica de aplicación y modelos

Los servicios de CoreServer representan los casos de uso: generación, llamado, ciclo de atención, rellamado, disponibilidad, acceso, reportes y administración. Los modelos representan los datos y sus reglas, como `Ticket`, `Service`, `OperatorWindowAssignment` y los estados del turno.

`AuthService` y `SessionRegistry` controlan las sesiones y permisos. Las credenciales se guardan mediante hashes de contraseña; los tokens de sesión se mantienen en memoria del servidor. La relación entre cuenta, asignación y turno se vuelve a comprobar en las operaciones protegidas.

### Acceso a datos y persistencia

Los contratos de repositorio expresan lo que necesita cada caso de uso. Sus implementaciones `Jdbc...Repository` contienen SQL parametrizado, lecturas, transacciones, bloqueos y conversión de resultados. Las comprobaciones que deben permanecer válidas durante una escritura se repiten dentro de esa transacción, junto con la modificación de los datos.

Por ejemplo, `TicketService` depende del contrato `TicketCreationRepository`. `JdbcTicketCreationRepository` consulta el servicio, bloquea su consecutivo y registra el ticket antes de confirmar ambos cambios. Si la operación falla, revierte la transacción. Las restricciones de MySQL complementan ese control para impedir identidades u ocupaciones duplicadas.

Las implementaciones `InMemory` se conservan como apoyo para desarrollo y comprobaciones. El ensamblado actual de CoreServer utiliza JDBC para la generación y atención. No hay una única conexión MySQL compartida entre todos los hilos ni un pool de conexiones incorporado en este corte.

### Motivo de esta organización

La separación permite que todas las estaciones operen sobre las mismas reglas y datos, mientras cada cliente mantiene su responsabilidad. Centralizar la persistencia evita que cada pantalla gestione SQL, credenciales de MySQL y bloqueos por su cuenta. Los contratos permiten modificar el acceso a datos sin trasladar esas decisiones a los controles gráficos.

Los hilos mantienen la disponibilidad de las interfaces y del servidor, mientras que las transacciones, los bloqueos y las restricciones protegen la consistencia. Son responsabilidades distintas: ejecutar solicitudes en paralelo no garantiza por sí solo que dos operaciones no tomen el mismo turno.

La implementación utiliza MySQL 8, conforme a la autorización de uso comunicada por el equipo. La conexión entre aplicaciones y CoreServer, y la conexión JDBC hacia MySQL, se configuran por separado. El modo TLS de las aplicaciones no implica que se haya configurado el mismo transporte para JDBC.

### Referencias de implementación

- [Ensamblado de CoreServer](../CoreServer/src/main/java/cr/ac/una/CoreServer/App.java) y [arranque del generador](../CostumerTurn/src/main/java/cr/ac/una/costumerturn/App.java).
- [Servidor](../CoreServer/src/main/java/cr/ac/una/server/TurnServer.java), [manejador de solicitudes](../CoreServer/src/main/java/cr/ac/una/server/TurnRequestHandler.java) y [centro de avisos](../CoreServer/src/main/java/cr/ac/una/server/DisplayEventHub.java).
- [Servicio de generación](../CoreServer/src/main/java/cr/ac/una/service/TicketService.java), [contrato de creación](../CoreServer/src/main/java/cr/ac/una/repository/TicketCreationRepository.java) e [implementación JDBC](../CoreServer/src/main/java/cr/ac/una/repository/JdbcTicketCreationRepository.java).
- [Guía de instalación](../INSTALACION.md), [configuración de red](../RED_Y_TLS.md) y [esquema](../database/script%20borrador.sql).

## 6. Diagrama general de componentes

![Componentes y conexiones del sistema](diagramas/componentes.svg)

[Abrir imagen PNG](diagramas/componentes.png) · [Archivo editable en diagrams.net](diagramas/componentes.drawio).

Las flechas continuas indican qué componente inicia la operación; las respuestas vuelven por la misma conexión. CostumerTurn consulta el catálogo y solicita turnos. OperatorSystem envía solicitudes de acceso, atención, consulta y administración. QueueDisplayScreen consulta una captura del estado público y mantiene un canal adicional de suscripción.

El inicio de sesión ocurre antes de disponer de un token; las solicitudes protegidas posteriores incluyen la sesión autenticada. No se exige iniciar sesión para generar turnos o consultar la pantalla pública.

La flecha discontinua representa los avisos que CoreServer envía por ese canal persistente. Un aviso indica que hay cambios; la pantalla vuelve a consultar los datos. No se envía la base completa por cada evento ni se consulta MySQL desde la pantalla. Los latidos permiten conservar y comprobar el canal sin obligar a consultar los datos cada vez.

Dentro de CoreServer se distinguen el acceso de red, los casos de uso y los repositorios JDBC. Esa separación es interna: no representa tres servidores desplegados ni un quinto proyecto Java. Solo CoreServer dispone de la configuración JDBC.

El diagrama representa componentes lógicos, no la distribución física entre computadoras. Se pueden ejecutar varias instancias de los clientes. Las conexiones fuera de loopback requieren TLS; la seguridad del enlace JDBC se configura por separado. La prueba física entre computadoras permanece pendiente.

## 7. Diseño de la base de datos

El sistema utiliza MySQL 8 con tablas InnoDB. El [script de creación](../database/script%20borrador.sql) es la referencia para los tipos, nulabilidad, valores predeterminados, claves e índices completos. El diagrama corresponde al esquema actual de diez tablas; sustituye el modelo preliminar de seis tablas.

![Tablas, claves y relaciones del sistema](diagramas/base_datos.svg)

[Abrir imagen PNG](diagramas/base_datos.png) · [Archivo editable en diagrams.net](diagramas/base_datos.drawio).

### Responsabilidad de las tablas

| Tabla | Responsabilidad |
| --- | --- |
| `operator` | Identidad del funcionario, nombre de acceso, perfil y estado. |
| `operator_credential` | Hash de la contraseña y fecha de actualización, vinculados al funcionario. No almacena la contraseña original. |
| `service` | Código estable, nombre, prefijo y estado del servicio. |
| `operator_service` | Servicios para los que está habilitado cada funcionario. |
| `window` | Identificador, estado y modalidad de la ventanilla: mixta o exclusivamente preferencial. |
| `window_service` | Servicios habilitados para cada ventanilla. |
| `operator_window_assignment` | Ocupación histórica de una ventanilla por un funcionario, con inicio y fin. |
| `app_user` | Registro opcional de una persona relacionada con tickets; no es una cuenta de acceso del personal. |
| `ticket_sequence` | Último consecutivo reservado para cada servicio. |
| `ticket` | Identidad, servicio, código visual, prioridad, estado, asignación, usuario opcional y fechas del turno. Incluye primer/último llamado y contador. |

### Lectura de las relaciones

En el diagrama, la multiplicidad se coloca junto a la tabla que describe. Por ejemplo, `operator 1 — 0..* operator_service` significa que un funcionario puede tener cero o muchas habilitaciones y cada habilitación pertenece a exactamente un funcionario.

| Tabla de referencia | Multiplicidad junto a ella | Tabla que contiene la FK | Multiplicidad junto a ella | Clave foránea |
| --- | --- | --- | --- | --- |
| `operator` | 1 | `operator_credential` | 0..1 | `operator_id` |
| `operator` | 1 | `operator_service` | 0..* | `operator_id` |
| `service` | 1 | `operator_service` | 0..* | `service_code` |
| `window` | 1 | `window_service` | 0..* | `window_id` |
| `service` | 1 | `window_service` | 0..* | `service_code` |
| `operator` | 1 | `operator_window_assignment` | 0..* | `operator_id` |
| `window` | 1 | `operator_window_assignment` | 0..* | `window_id` |
| `service` | 1 | `ticket_sequence` | 0..1 | `service_id` |
| `service` | 1 | `ticket` | 0..* | `service_id` |
| `operator_window_assignment` | 0..1 | `ticket` | 0..* | `assignment_id` |
| `app_user` | 0..1 | `ticket` | 0..* | `user_id` |

Las once relaciones corresponden a claves foráneas reales. Un cruce de líneas no añade una relación; el puente visual indica que los conectores pasan sin unirse. `PK` identifica la clave primaria, `FK` una foránea y `UQ` una restricción única de una columna. La unicidad compuesta por servicio y número se explica debajo.

### Relaciones múltiples y conservación del historial

`operator_service` y `window_service` resuelven relaciones muchos a muchos. Sus claves primarias compuestas impiden repetir la misma pareja. La atención de un servicio exige que esté habilitado tanto para el funcionario como para la ventanilla; no basta con uno de los dos enlaces.

`operator_window_assignment` representa una ocupación a lo largo del tiempo. Un funcionario y una ventanilla pueden aparecer en muchas asignaciones históricas, pero cada uno puede tener como máximo una abierta. Cerrar una asignación registra su fin; ocupar de nuevo la ventanilla crea otra fila.

El ticket referencia esa asignación para obtener el funcionario y la ventanilla que lo atendieron. Así, cambiar después al ocupante de una ventanilla no cambia el responsable de los tickets anteriores. Esta relación conserva identidades históricas; no implica que se guarde una copia inmutable de cada nombre del catálogo.

La asignación del ticket es opcional únicamente mientras está `EN_ESPERA`; los estados `LLAMADO`, `EN_ATENCION` y `FINALIZADO` la exigen mediante un CHECK. La relación con `app_user` es opcional en todos los estados. La emisión actual no completa automáticamente `user_id`.

Un servicio puede tener como máximo una fila en `ticket_sequence`. La FK no obliga a crearla: la instalación inicial la prepara y la generación rechaza un servicio sin consecutivo. La futura administración de servicios debe conservar esta condición y no reiniciar números existentes.

### Restricciones e integridad

- El código visual del ticket es único. También lo es la pareja `(service_id, sequential_number)`, cuyo número debe ser positivo.
- El prefijo del servicio y el nombre de acceso del funcionario tienen restricciones únicas. `app_user.username` no tiene un índice único en el DDL actual; la administración comprueba sus duplicados en su recorrido transaccional.
- Las columnas calculadas `active_operator_id` y `active_window_id` solo tienen valor en asignaciones abiertas. Sus índices únicos impiden dos asignaciones abiertas para un mismo funcionario o una misma ventanilla.
- `active_assignment_id` solo tiene valor en tickets `LLAMADO` o `EN_ATENCION`; su índice único limita a uno el turno activo de cada asignación. Los tickets finalizados permanecen como historial.
- Los CHECK restringen estados, modalidad, prioridad, asociación y presencia/orden de fechas. Las columnas calculadas no son FK adicionales.
- Las FK conservan las referencias. La aplicación utiliza bajas lógicas y rechaza cambios incompatibles, en lugar de borrar el historial para resolver dependencias.

Los datos de cada funcionario, servicio y ventanilla se conservan en su tabla correspondiente. Las habilitaciones y las ocupaciones históricas se representan mediante relaciones, evitando repetir listas de servicios o los datos completos del funcionario dentro del ticket. `visual_code` y las columnas calculadas son redundancias controladas para identificación y restricciones; no representan nuevas entidades.

Las claves foráneas aseguran existencia, pero no comprueban por sí solas actividad, permisos ni compatibilidad. Esas reglas se verifican en el servidor dentro de sus operaciones transaccionales, junto con los bloqueos necesarios.

### Fechas, rellamado e instalación

El ticket guarda creación, primer llamado, último llamado, inicio y fin. Las cuatro fechas de atención usan `TIMESTAMP(6)`; creación y fechas de asignación mantienen precisión de segundos. El rellamado conserva primer llamado, último llamado y contador, sin una fila independiente por cada repetición. Tampoco existe una bitácora individual de cada alta o baja de habilitación.

Para una instalación nueva se ejecutan el esquema y los [datos iniciales](../database/datos_prueba.sql). Para una base anterior se revisan las [migraciones](../database/migraciones), sin reinicializar datos ni contadores. El procedimiento reproducible está en [INSTALACION.md](../INSTALACION.md).

Los dos diagramas se contrastaron con el corte `cc9405e`. Deben comprobarse otra vez al integrar los cambios de servicios y ventanillas; no representan por anticipado esa implementación.
