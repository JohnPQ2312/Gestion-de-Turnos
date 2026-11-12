# Ensayo sobre la base compartida — 12 de noviembre de 2026

Base: gamabasis_p3g9. Las migraciones 001, 002 y 003 fueron ejecutadas por el usuario en phpMyAdmin después de consultar la estructura. El usuario creó las credenciales de operador1 y operador2 mediante CredentialSetup. No se guardan contraseñas ni hashes en esta evidencia.

## Registro aportado por el usuario (consultas SQL)

| Momento | Ticket / código / consecutivo / asignación | Estado | Primer llamado (UTC) | Último llamado (UTC) | Contador |
| --- | --- | --- | --- | --- | ---: |
| Antes de rellamar | 2 / A002 / 2 / 1 | LLAMADO | 2026-11-10 20:31:22.000000 | 2026-11-10 20:31:22.000000 | 1 |
| Tras rellamar | 2 / A002 / 2 / 1 | LLAMADO | 2026-11-10 20:31:22.000000 | 2026-11-12 15:53:58.583800 | 2 |
| Tras iniciar atención | 2 / A002 / 2 / 1 | EN_ATENCION | 2026-11-10 20:31:22.000000 | 2026-11-12 15:53:58.583800 | 2 |

El usuario confirmó que ambas pantallas recibieron el rellamado, que la pausa deshabilitó el botón y que reanudar volvió a habilitarlo. El inicio quedó guardado a las 2026-11-12 15:58:56.495707 UTC.

## Observación directa de las aplicaciones nativas

Se inspeccionó OperatorSystem con Computadora. El operador 1 tenía A002 en atención. Se pulsó Finalizar atención y se confirmó el panel correspondiente al mismo código. Después se observó:

- Operador 1 sin turno activo, con Última finalizada aquí: A002 y Llamar siguiente disponible.
- Dos ventanas distintas de QueueDisplayScreen conectadas, ambas con A002 Finalizado, Llamados: 2 y la misma ventanilla y fechas.
- El operador 2 estaba sin turno activo al iniciar la inspección.

## Comprobación JDBC posterior, de solo lectura

Se usó DatabaseConnectionFactory con la configuración privada local, verificando el host y el catálogo autorizados. La sesión de lectura se fijó en UTC. La consulta confirmó A002 FINALIZADO y ended_at = 2026-11-12 16:02:35.932258. El primer llamado, último llamado, contador e identificadores coinciden con los aportados por el usuario; las fechas están en orden y no quedan activos en la asignación 1.

También se verificaron la precisión de seis decimales de las cuatro fechas de atención, la restricción ck_ticket_call_history, el índice idx_ticket_last_called y la existencia de credenciales de las dos cuentas activas de prueba. Esta lectura no modificó registros.

El resultado de esa comprobación está en [base-compartida-final.log](base-compartida-final.log).

Este es un ensayo funcional sobre la base remota usando clientes y CoreServer ejecutados en un mismo equipo. No es una prueba de despliegue entre computadoras ni una medición de rendimiento bajo carga. Los resultados locales de 24 + 10 + 15 escenarios permanecen separados de este registro.
