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

# Prueba de llamados simultáneos sobre una misma asignación

`SameAssignmentCallingCheck.java` envía dos `CALL_NEXT` sobre **la misma** asignación, liberados por una señal común. Comprueba que el sistema admita un solo llamado y rechace el otro por ocupación, y después verifica el estado guardado mediante el servidor.

## Preparación

Coordinar con el equipo quién prepara los datos y cuándo se ejecuta. Ninguna otra operación debe modificar la asignación ni la cola durante la prueba.

1. Iniciar CoreServer.
2. Elegir una asignación vigente (por defecto, operador 1 en ventanilla 1) y comprobar desde OperatorSystem que no tenga turno activo. Si conserva un turno de pruebas anteriores, como A008 o A009, completar o revisar su atención antes.
3. Tener al menos dos turnos `EN_ESPERA` compatibles con esa asignación y menos de 50 turnos en espera en total.

La herramienta detiene la ejecución si la asignación no existe, no está libre o hay menos de dos turnos en espera. La compatibilidad no se puede consultar por el protocolo; se comprueba antes en MySQL con la consulta siguiente, que debe devolver al menos dos filas:
```sql
SELECT t.ticket_id, t.visual_code, t.`priority`, t.created_at
FROM ticket t
JOIN operator_window_assignment a ON a.assignment_id = 1   -- cambiar 1 por la asignación de la prueba
JOIN operator_service os ON os.operator_id = a.operator_id AND os.service_code = t.service_id
JOIN window_service ws ON ws.window_id = a.window_id AND ws.service_code = t.service_id
JOIN `window` w ON w.window_id = a.window_id
JOIN service s ON s.service_code = t.service_id AND s.service_state = 'ACTIVO'
WHERE t.ticket_state = 'EN_ESPERA'
  AND (w.attention_mode = 'MIXED' OR t.`priority` = TRUE)
ORDER BY t.`priority` DESC, t.created_at, t.ticket_id;
```

## Ejecución

Desde PowerShell, en la raíz del repositorio:

```powershell
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
java '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' pruebas/SameAssignmentCallingCheck.java 1 1
```

Los dos números son el operador y la ventanilla de la asignación. No reintenta escrituras.

## Resultado esperado

- Exactamente una respuesta `OK;CALLED` para el operador y la ventanilla de la asignación.
- Exactamente una respuesta `ERROR` con el mensaje de ocupación: «La asignación, el operador o la ventanilla ya tienen un turno activo.» Un error distinto (por ejemplo, de base de datos) cuenta como falla.
- `GET_ACTIVE_TICKET` devuelve el turno llamado, en estado `LLAMADO`.
- El turno llamado ya no aparece en espera, los demás turnos siguen en espera y la espera baja exactamente en uno.

Al final la herramienta imprime dos consultas para confirmar en phpMyAdmin el estado de los turnos involucrados y que la asignación tiene un solo turno activo. Las líneas `[tiempo]` de la consola de CoreServer registran el procesamiento de cada `CALL_NEXT`.

## Resultados

| Fecha | Asignación | Respuestas | Turno llamado | Comprobaciones | MySQL |
| --- | --- | --- | --- | --- | --- |
| Pendiente | — | — | — | — | — |

Pendiente de ejecución contra MySQL. La herramienta se comprobó únicamente contra un servidor simulado: con un servidor que serializa el llamado reportó un llamado y un rechazo por ocupación; con un servidor defectuoso que entregaba dos turnos reportó cuatro comprobaciones fallidas; y se detuvo con una asignación ocupada o inexistente. Esto valida la lógica de la herramienta, no el comportamiento de CoreServer con MySQL.

# Prueba de un único turno elegible para dos ventanillas

`SingleEligibleTicketCheck.java` envía `CALL_NEXT` desde dos asignaciones distintas, liberados por una señal común, cuando existe un solo turno en espera compatible con ambas. Comprueba que solo una ventanilla obtenga el turno y que la otra reciba `OK;NO_TICKET`.

## Preparación

Coordinar con el equipo quién prepara los datos y cuándo se ejecuta. Nadie debe generar ni llamar turnos durante la prueba.

1. Iniciar CoreServer.
2. Comprobar que las dos asignaciones (por defecto, operador 1 en ventanilla 1 y operador 2 en ventanilla 2) estén libres.
3. Dejar exactamente un turno `EN_ESPERA` en todo el sistema, compatible con ambas asignaciones.

La herramienta se detiene si alguna asignación no existe o no está libre, o si no hay exactamente un turno en espera. La compatibilidad se comprueba antes en MySQL; la consulta siguiente debe devolver dos filas, una por asignación, con el mismo turno:

```sql
SELECT a.assignment_id, a.window_id, t.visual_code, t.`priority`, w.attention_mode
FROM ticket t
JOIN operator_window_assignment a ON a.assignment_id IN (1, 2)   -- cambiar por las asignaciones de la prueba
JOIN operator_service os ON os.operator_id = a.operator_id AND os.service_code = t.service_id
JOIN window_service ws ON ws.window_id = a.window_id AND ws.service_code = t.service_id
JOIN `window` w ON w.window_id = a.window_id
JOIN service s ON s.service_code = t.service_id AND s.service_state = 'ACTIVO'
WHERE t.ticket_state = 'EN_ESPERA'
  AND (w.attention_mode = 'MIXED' OR t.`priority` = TRUE);
```

## Ejecución

Desde PowerShell, en la raíz del repositorio:

```powershell
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
java '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' pruebas/SingleEligibleTicketCheck.java 1 1 2 2
```

Los números son operador y ventanilla de la primera asignación y de la segunda. No reintenta escrituras.

## Resultado esperado

- Exactamente una ventanilla recibe `OK;CALLED` con el turno en disputa.
- La otra recibe `OK;NO_TICKET`. Un `ERROR` o un segundo `OK;CALLED` cuenta como falla.
- `GET_ACTIVE_TICKET` muestra el turno `LLAMADO` en la ventanilla ganadora y ningún turno activo en la otra.
- No quedan turnos en espera.

Al final la herramienta imprime dos consultas para confirmar en phpMyAdmin el estado del turno y que cada asignación tiene como máximo un turno activo.

## Resultados

| Fecha | Asignaciones | Respuestas | Ventanilla ganadora | Comprobaciones | MySQL |
| --- | --- | --- | --- | --- | --- |
| Pendiente | — | — | — | — | — |

Pendiente de ejecución contra MySQL. La herramienta se comprobó únicamente contra un servidor simulado: con un servidor que serializa el llamado reportó un llamado y un `OK;NO_TICKET`; con un servidor defectuoso que entregaba el mismo turno a ambas ventanillas reportó tres comprobaciones fallidas; y se detuvo cuando había dos turnos en espera. Esto valida la lógica de la herramienta, no el comportamiento de CoreServer con MySQL.
