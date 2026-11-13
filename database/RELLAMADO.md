# Rellamado de turnos

El botón **Volver a llamar** repite el llamado del mismo ticket. Solo puede usarlo el funcionario dueño de la asignación, cuando está abierta, la ventanilla está ACTIVA y el turno continúa LLAMADO. Una ventanilla PAUSADA debe reanudarse primero. No se rellaman turnos en espera, en atención o finalizados.

El rellamado no genera otro ticket, no consume consecutivos y no libera la asignación. La validación del servidor se mantiene aunque se manipule el cliente.

## Preparar una base existente

La migración se validó en MySQL 8.0.32 local, sobre bases desechables, y se aplicó a `gamabasis_p3g9` para comprobar el rellamado sobre la base compartida. No volver a aplicarla en esa base. Para otra instalación, comprobar primero su estructura y seguir el procedimiento que corresponda. El [resumen de comprobaciones](../pruebas/README.md#comprobaciones-y-límites) distingue ambos entornos.

1. Confirmar que la base ya corresponde a las migraciones 001 y 002. Consultar [la guía de acceso](INICIALIZAR_ACCESO.md) si todavía faltan.
2. Detener CoreServer y cerrar los clientes para actualizar la versión completa.
3. En phpMyAdmin seleccionar la base correspondiente, por ejemplo `gamabasis_p3g9`. Comprobar si ya existen las columnas:

   ```sql
   SHOW COLUMNS FROM ticket LIKE 'last_called_at';
   SHOW COLUMNS FROM ticket LIKE 'call_count';
   ```

4. Si ambas faltan, ejecutar una sola vez [003_rellamado_ticket.sql](migraciones/003_rellamado_ticket.sql), en su orden completo. Si alguna ya existe, revisar el estado de la migración antes de repetirla.
5. Compilar los proyectos actualizados en NetBeans. Iniciar CoreServer y después los clientes. CoreServer, OperatorSystem y QueueDisplayScreen deben usar esta versión del bloque; CostumerTurn conserva su contrato de emisión.

Sobre una base existente no ejecutar de nuevo `esquema.sql` ni `datos_prueba.sql`. La migración 003 conserva los tickets: amplía la precisión de las fechas e inicializa los dos campos nuevos del historial.

La migración no es una transacción única: MySQL confirma las operaciones de estructura. Si falla un paso, revisar cuál se completó antes de continuar. El UPDATE de inicialización solo completa metadatos ausentes y no reinicia contadores que ya registran rellamados.

## Instalación nueva

En una base vacía, ejecutar `esquema.sql`, que ya incluye estos campos y restricciones. Los datos ficticios de `datos_prueba.sql` se usan únicamente en instalaciones nuevas de pruebas. No aplicar además la migración 003.

## Historial y nombres

| Campo | Significado |
| --- | --- |
| `called_at` | Fecha del primer llamado. No se modifica al rellamar. |
| `last_called_at` | Fecha del último llamado confirmado, inicial o repetido. |
| `call_count` | Cantidad de llamados: 0 en espera, 1 al llamar por primera vez, 2 tras el primer rellamado. |

Para los tickets anteriores se conserva `called_at` y se copia a `last_called_at`, con contador 1; los turnos en espera mantienen fecha nula y contador 0. No se reconstruyen repeticiones anteriores que no estuvieran registradas.

Las fechas de llamado, último llamado, inicio y fin usan TIMESTAMP(6). Los microsegundos permiten ordenar llamadas próximas sin perder el primer llamado ni redondear al futuro. Los repositorios truncan a esa precisión antes de guardar. La fecha de creación conserva su precisión anterior. La configuración de conversión horaria sigue siendo la existente.

`TicketRecallService` valida la petición; `JdbcTicketRecallRepository` la guarda de forma atómica; `RecalledTicket` representa la confirmación persistida. Dentro de la transacción se bloquean, en ese orden, asignación, funcionario, ventanilla y ticket. Se comprueba nuevamente quién es el dueño antes de modificar el contador.

## Protocolo

Solicitud autenticada:

```text
AUTH;<token>;RECALL_TICKET;<assignment_id>;<ticket_id>
OK;RECALLED;<assignment_id>;<ticket_id>;<call_count>;<last_called_at>
```

Las fechas y los textos usan la codificación del protocolo existente. La respuesta se envía después de confirmar la transacción. Solo entonces se publica DISPLAY_CHANGED a las pantallas. Los rechazos no incrementan el contador ni publican avisos. Una respuesta perdida no provoca reintentos automáticos.

La pantalla pública solicita `GET_DISPLAY_SNAPSHOT_V2`. Su respuesta comienza con `OK;DISPLAY_SNAPSHOT_V2;<cantidad_en_espera>;<cantidad_recientes>` y cada ticket contiene nueve campos:

```text
ticket_id;visual_code;service_name;priority;state;window_id;called_at;last_called_at;call_count
```

Los llamados recientes se ordenan por `last_called_at` e identificador. La pantalla compara ticket y contador para detectar una nueva repetición, muestra primer/último llamado y destaca el panel durante cuatro segundos. Al iniciar o finalizar la atención conserva el contador como historial, sin seguir anunciando que se está llamando al cliente. Una reconexión recupera los datos persistidos.

El comando anterior `GET_DISPLAY_SNAPSHOT` conserva sus siete campos. Así siguen funcionando las herramientas de concurrencia existentes. La nueva pantalla requiere un CoreServer actualizado para consultar V2.

## Comprobación manual al integrar con la base compartida

1. Iniciar sesión con un funcionario y elegir una asignación propia libre.
2. Generar y llamar un turno de prueba; abrir dos pantallas públicas.
3. Pulsar **Volver a llamar**. Deben conservarse el código y la ventanilla; ambas pantallas deben mostrar el nuevo llamado y su contador.
4. Pausar la ventanilla: el rellamado queda deshabilitado. Reanudarla: vuelve a habilitarse mientras el turno siga LLAMADO.
5. Iniciar y finalizar la atención: ya no debe ofrecerse rellamado. El historial permanece.

Consultar el ticket usado, sustituyendo el código de ejemplo:

```sql
SELECT ticket_id, visual_code, sequential_number, assignment_id,
       ticket_state, called_at, last_called_at, call_count, started_at, ended_at
FROM ticket
WHERE visual_code = 'CODIGO_DE_PRUEBA';
```

Si se pierde la confirmación, revisar el aviso público y esta información antes de repetir. El mismo estado LLAMADO no demuestra por sí solo que el rellamado falló.
