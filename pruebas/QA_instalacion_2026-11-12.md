# QA de instalación limpia — 12 de noviembre de 2026

## Versión y entorno

Base revisada: `464de03759c93a36333bcb81ee95afcfd380e4f6` (Configuración de red y transporte TLS).

Se extrajeron los archivos versionados mediante `git archive` a una carpeta nueva, sin reutilizar los `target`, las configuraciones privadas ni los recursos de ejecución de la carpeta del equipo. No fue una descarga nueva desde GitHub.

Entorno: Windows, JDK 25 con destino de compilación Java 21, JavaFX 13 y MySQL 8.0.32 local. Los cuatro proyectos compilaron con Maven en modo sin conexión usando dependencias ya disponibles en la caché. Esto no comprueba la primera descarga de dependencias en una computadora sin caché.

Se creó únicamente la base desechable `turnos_instalacion_qa`, en `127.0.0.1:33317`. El servidor de aplicaciones utilizó TLS en `localhost:33322`, con almacenes privados de prueba fuera del repositorio. No se utilizó ni modificó la base compartida, el firewall o el almacén de certificados de Windows.

## Preparación comprobada

- Ejecución del esquema actual y después los datos iniciales, sin aplicar migraciones adicionales.
- Resultado inicial: 10 tablas, 5 funcionarios, 3 servicios, 4 ventanillas, 3 asignaciones, 0 credenciales y 0 turnos.
- Creación de credenciales mediante la clase real `CredentialSetup` para `admin`, `amora` y `lvargas`. La herramienta se ejecutó en procesos separados y recibió contraseñas de prueba por entrada estándar, sin argumentos ni contraseñas en las evidencias.
- Compilación de CoreServer, CostumerTurn, OperatorSystem y QueueDisplayScreen desde la copia limpia.

Evidencias: [instalación](evidencias/instalacion_2026-11-12/instalacion.log), [acceso administrativo](evidencias/instalacion_2026-11-12/credencial-admin.log), [amora](evidencias/instalacion_2026-11-12/credencial-amora.log) y [lvargas](evidencias/instalacion_2026-11-12/credencial-lvargas.log).

Registros de compilación: [CoreServer](evidencias/instalacion_2026-11-12/compilacion-CoreServer.log), [CostumerTurn](evidencias/instalacion_2026-11-12/compilacion-CostumerTurn.log), [OperatorSystem](evidencias/instalacion_2026-11-12/compilacion-OperatorSystem.log) y [QueueDisplayScreen](evidencias/instalacion_2026-11-12/compilacion-QueueDisplayScreen.log). Al guardar el registro de instalación se corrigió una tilde dañada por la captura de consola, sin alterar el resultado.

## Recorrido de las aplicaciones reales

Se iniciaron CoreServer, el generador, dos pantallas públicas y dos ventanas de operador usando sus clases `App`, controladores, FXML y tareas reales. Este recorrido compartió una JVM de prueba; la comunicación utilizó sockets TLS y repositorios JDBC contra la nueva base, sin respuestas simuladas.

| Caso | Resultado comprobado |
| --- | --- |
| 1. Arranque | Las aplicaciones cargaron con configuración externa TLS y la base recién instalada. |
| 2. Sesiones | Dos funcionarios ingresaron y vieron únicamente su asignación; los controles administrativos permanecieron ocultos. |
| 3. Generación | La interfaz emitió A001 regular, A002 preferencial y B001 regular; ambas pantallas recibieron los turnos. |
| 4. Llamado | La ventanilla 1 tomó A002 y la 2 tomó A001, con asignaciones diferentes y sin ocupación duplicada. Fue un recorrido secuencial, no una nueva prueba de concurrencia. |
| 5. Rellamado | A002 conservó su primer llamado y alcanzó contador 2; ambas pantallas mostraron la repetición. |
| 6. Pausa | Pausar deshabilitó el rellamado; reanudar volvió a habilitarlo. |
| 7. Ciclo | Ambas atenciones se iniciaron y finalizaron; las fechas persistidas conservaron su orden y B001 quedó en espera. |
| 8. Personas | Tras cerrar sesión e ingresar como ADMIN, la navegación abrió la vista con los cinco funcionarios. |
| 9. Consultas | Las cinco consultas devolvieron resultados sobre las atenciones de esta instalación. |
| 10. Asignaciones | La vista administrativa recuperó las habilitaciones y las tres asignaciones iniciales. |
| 11. Persistencia | La base conservó tres turnos, dos finalizados y B001 en espera; el consecutivo INFO quedó en 2. |

**Resultado: 11 escenarios aprobados, 0 fallidos.** [Registro del recorrido](evidencias/instalacion_2026-11-12/recorrido-integrado.log).

Las capturas del rellamado, estado final, Personas, Consultas y Asignaciones se revisaron visualmente. En el tamaño probado los datos y controles principales fueron legibles. Las atenciones automatizadas duraron menos de un segundo; el reporte mostró `0:00:00` por su formato en segundos, sin demostrar un fallo de cálculo. Esta revisión no sustituye una evaluación de accesibilidad o de todos los tamaños de pantalla.

## Servidor y clientes en procesos separados

Se inició `cr.ac.una.CoreServer.App` en un proceso independiente. Otro proceso cargó únicamente las clases de los tres clientes, sin clases de CoreServer, controlador JDBC ni `db.properties` en su ruta de clases. Todas las operaciones siguientes pasaron por TLS al servidor real:

| Caso | Resultado comprobado |
| --- | --- |
| 1. Datos públicos | Catálogo de tres servicios y captura pública con B001 en espera. |
| 2. Sesión y consultas | Inicio administrativo y ejecución de las cinco consultas. |
| 3. Usuarios | Creación de un usuario de turnos, cambio de nombre, desactivación y recuperación del dato actualizado. |
| 4. Configuración | Cierre de la asignación 3 libre, habilitación de INFO para ADMIN y apertura de una nueva asignación en la ventanilla 3. El historial se conservó. Esa ventanilla exclusiva no tomó B001 regular. |

**Resultado: 4 escenarios aprobados, 0 fallidos.** [Registro entre procesos](evidencias/instalacion_2026-11-12/procesos-separados.log).

Los procesos permanecieron en la misma computadora. Esto comprueba la separación entre aplicaciones, pero no la conectividad LAN, el firewall ni los certificados con la dirección de otra computadora.

## Cambios resultantes y pendientes

No se encontró un fallo funcional en este recorrido. Se agregó [INSTALACION.md](../INSTALACION.md), se enlazó desde el README y se corrigió la guía de acceso: cuenta ADMIN inicial, permisos administrativos ya disponibles, revocación de sesiones y uso actual de TLS.

Los registros corresponden a herramientas auxiliares de esta sesión, no a una suite permanente ejecutada por Maven. No se añadieron claves, almacenes de certificados, configuraciones privadas ni credenciales al repositorio. Al terminar se detuvieron el servidor de aplicaciones de prueba y MySQL local.

Siguen pendientes el ensayo físico entre computadoras, las mediciones con la base remota, la integración del bloque de servicios/ventanillas de Joshua y la actualización final de diagramas, documento técnico y empaquetado. Estos resultados no declaran cerrado el proyecto completo.
