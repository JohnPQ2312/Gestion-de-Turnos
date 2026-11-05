# Prueba de llamados concurrentes

Esta prueba prepara dos conexiones a CoreServer y libera sus solicitudes CALL_NEXT desde una misma señal. Comprueba las respuestas y vuelve a leer los turnos activos mediante el servidor. CoreServer ejecuta las operaciones reales sobre la base configurada.

## Preparación

1. Usar Java 21 o superior e iniciar CoreServer.
2. Abrir los dos operadores y comprobar las asignaciones del operador 1 con ventanilla 1 y del operador 2 con ventanilla 2.
3. Completar las atenciones existentes para que ambas asignaciones queden libres.
4. Preparar dos turnos preferenciales de Información general como primeros turnos de la lista de espera.
5. Mantener estables las asignaciones y la cola durante la prueba.

La herramienta identifica las asignaciones activas mediante LIST_ASSIGNMENTS y consulta GET_ACTIVE_TICKET antes de enviar los llamados. Si los requisitos de preparación no se cumplen, detiene la ejecución.

## Ejecución

Desde PowerShell, en la raíz del repositorio y con el JDK disponible en PATH:

```powershell
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
java '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' pruebas/ConcurrentCallingCheck.java
```

También se puede sustituir java por la ruta completa al ejecutable del JDK. La prueba utiliza 127.0.0.1:5000. No necesita las credenciales de la base.

Envía dos llamados y deja los turnos llamados para inspeccionarlos. Si se agota la espera, revisar la consola de CoreServer y las asignaciones guardadas antes de repetir. La herramienta no reintenta escrituras automáticamente.

## Ejecución observada el 4 de noviembre de 2026

Ambas asignaciones estaban libres. Los turnos preparados eran A008 y A009, ambos preferenciales de Información general.

| Ventanilla / operador | Asignación | Turno recibido | Identificador interno | Estado posterior | Respuesta |
| --- | --- | --- | --- | --- | --- |
| 1 / 1 | 1 | A009 | 11 | LLAMADO | 6518 ms |
| 2 / 2 | 2 | A008 | 9 | LLAMADO | 5525 ms |

La separación entre los inicios de envío fue de 0,326 ms. Ambas solicitudes obtuvieron códigos distintos y GET_ACTIVE_TICKET confirmó las asignaciones correspondientes. La ventanilla 2 recibió el preferencial más antiguo, A008. El orden de presentación de las respuestas por la herramienta no determina cuál solicitud se procesó primero.

El usuario ejecutó después las consultas SQL siguientes en phpMyAdmin. A008 quedó con prioridad 1, asignación 2 y llamado a las 03:22:35 del 5 de noviembre. A009 quedó con prioridad 1, asignación 1 y llamado a las 03:22:36. Las horas de esa sesión MySQL son UTC y corresponden al 4 de noviembre en Costa Rica.

```sql
SELECT
    t.ticket_id, t.visual_code, t.`priority`, t.ticket_state,
    t.assignment_id, a.operator_id, a.window_id, t.called_at
FROM gamabasis_p3g9.ticket t
JOIN gamabasis_p3g9.operator_window_assignment a
    ON a.assignment_id = t.assignment_id
WHERE t.visual_code IN ('A008', 'A009')
ORDER BY t.visual_code;

SELECT a.window_id, COUNT(*) AS turnos_activos
FROM gamabasis_p3g9.ticket t
JOIN gamabasis_p3g9.operator_window_assignment a
    ON a.assignment_id = t.assignment_id
WHERE t.ticket_state IN ('LLAMADO', 'EN_ATENCION')
GROUP BY a.window_id
HAVING COUNT(*) > 1;
```

La primera consulta devolvió los dos registros esperados. La segunda devolvió cero filas.

## Alcance y continuación

Se comprobó una ejecución de dos llamados coordinados contra MySQL. Quedan por ampliar escenarios con un solo turno elegible, solicitudes sobre una misma asignación, generación concurrente y fallos de transacción.

Los tiempos de respuesta de 5,5 y 6,5 segundos quedan registrados para medir y optimizar las consultas y conexiones. Son tiempos de llamado completos, distintos del tiempo que tarda la pantalla en mostrar un aviso.

Tras esta ejecución, A008 y A009 permanecen LLAMADO. Para trabajar desde los operadores, utilizar Actualizar asignaciones y revisar el estado recuperado antes de iniciar o finalizar sus atenciones.
