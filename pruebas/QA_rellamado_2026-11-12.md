# Validación del rellamado — 12 de noviembre de 2026

Base del desarrollo: `7b5541f`. El bloque se implementó y validó primero en local y después se completó el ensayo funcional sobre la base compartida el 12 de noviembre de 2026.

## Resultado y alcance

Los cuatro proyectos compilan. Se ejecutaron **24 escenarios de servidor, 10 de interfaz y 15 de regresión**, todos aprobados, utilizando las clases compiladas de la carpeta real del proyecto. La regresión repite las herramientas del bloque anterior para comprobar su compatibilidad; no representa 15 funcionalidades nuevas.

Se utilizó MySQL 8.0.32 portátil en 127.0.0.1:33317, con las bases desechables turnos_qa y turnos_migracion_qa. El servidor TCP de producción se ejecutó en el puerto 33318 y los clientes reales se conectaron a él. La ejecución fue con JDK 25.0.3, compilación para Java 21 y JavaFX 13. Estas baterías aisladas no consultaron ni modificaron la base compartida. El ensayo posterior sobre ella se registra por separado al final de este informe.

## Implementación

- `TicketRecallService` valida los identificadores y `JdbcTicketRecallRepository` guarda el rellamado dentro de una transacción. Bloquea asignación, funcionario, ventanilla y ticket, y revalida propietario, actividad y estado.
- Solo se rellama un ticket propio LLAMADO con ventanilla ACTIVA. No cambia su código, consecutivo, asignación ni primer `called_at`; actualiza `last_called_at` y `call_count`.
- La migración 003 conserva los tickets y amplía las fechas de atención a microsegundos. Su inicialización histórica solo completa metadatos ausentes; no reinicia rellamados ya registrados. El DDL de instalación nueva incluye los mismos campos, índice y restricción.
- OperatorSystem incorpora **Volver a llamar**, su habilitación por estado y una confirmación visible. Una respuesta perdida produce una advertencia y consulta del estado, sin volver a enviar la escritura.
- QueueDisplayScreen usa el snapshot V2, muestra primer y último llamado y reconoce el contador persistido. Un nuevo rellamado destaca el panel durante cuatro segundos. Al comenzar la atención conserva el historial sin seguir anunciando el llamado.
- El snapshot anterior mantiene sus siete campos para las herramientas existentes. Los avisos se publican después del commit; una cola de notificaciones puede agrupar cambios y la pantalla recupera la última revisión persistida.

## Pruebas destacadas

**Persistencia y servidor:** instalación limpia; migración sobre tickets en los cuatro estados; conservación de fechas y consistencia con DDL; backfill que respeta rellamados existentes; inicialización en un llamado; conservación de identidad/consecutivo/ocupación; veinte repeticiones rápidas seguidas de inicio y finalización; reportes y sus tiempos; contador agotado; reloj atrasado; identificadores/propietario/estados inválidos; pausa; cuentas y ventanillas inactivas; asignación cerrada; restricciones SQL y rollback real ante un error provocado.

**Concurrencia:** dos rellamados simultáneos reciben contadores distintos; cinco rondas de rellamado contra pausa y cinco contra inicio mantienen estado y cronología. No se duplican tickets ni se pierde la ocupación.

**Protocolo:** autenticación y pertenencia obligatorias; cliente de operador real; compatibilidad V1 y datos de revisión V2; dos suscriptores reciben el cambio después de guardarse; un rechazo no publica avisos ni incrementa el contador; perder la respuesta no reenvía el rellamado.

**Interfaz:** botón visible al tamaño mínimo; doble clic que genera una única repetición; confirmación; dos pantallas muestran la misma revisión y el destaque; reapertura recupera el contador; pausa y reanudación; sucesivas repeticiones con el mismo código; ocultamiento al iniciar atención; finalización y conservación del historial; un turno nuevo no hereda el aviso anterior; confirmación perdida con advertencia y sin reenvío automático. Se inspeccionaron capturas de ambas vistas.

**Regresión:** las tres herramientas de concurrencia autenticadas siguen pasando sus casos normales, fallos de preparación, identidad incorrecta, cierre de sesiones, códigos de salida y detección de confirmaciones incoherentes. Los dos errores de permisos que quedaron al pausar se resolvieron al ejecutar con acceso de escritura; los 15 casos finalizaron aprobados.

## Evidencias

- [Servidor: 24 casos](evidencias/rellamado_2026-11-12/servidor.log).
- [Interfaz: 10 casos](evidencias/rellamado_2026-11-12/interfaz.log).
- [Regresión: 15 casos](evidencias/rellamado_2026-11-12/regresion-herramientas.log).
- [Matriz de los 49 escenarios](evidencias/rellamado_2026-11-12/matriz.csv).

Los auxiliares y capturas permanecen en el espacio local de QA, fuera del repositorio. Esta validación no está integrada en Maven/CI. No se sumaron las rondas internas ni ejecuciones repetidas para aumentar el total.

## Ensayo en la base compartida

El usuario aplicó las migraciones 001, 002 y 003 y configuró las dos cuentas de operadores. A002 pasó de un llamado a dos, conservó su primer llamado y llegó a FINALIZADO. El usuario confirmó el aviso en dos pantallas y la pausa/reanudación; se comprobó directamente la finalización desde OperatorSystem, la actualización de ambas pantallas y la persistencia mediante una consulta JDBC de solo lectura. Ver [el registro de la base compartida](evidencias/rellamado_2026-11-12/base-compartida.md) y [su resultado final](evidencias/rellamado_2026-11-12/base-compartida-final.log).

La instalación y el procedimiento para otras bases se describen en [database/RELLAMADO.md](../database/RELLAMADO.md). No repetir migraciones ya aplicadas. Quedan fuera de este ensayo el despliegue entre computadoras y la medición de rendimiento bajo carga.

El siguiente bloque funcional del plan es la administración de servicios y ventanillas; deberá respetar prefijos, consecutivos, asignaciones y turnos existentes. Los diagramas y la documentación de entrega se actualizan contra la versión que finalmente se integre.
