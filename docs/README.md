# Documentación del proyecto

Fuente editable: [documento técnico](documento_tecnico.md).

Este trabajo sigue los catorce apartados de la sección 23.C del enunciado. El borrador se completa por bloques; la versión de referencia inicial es `cc9405e`.

## Estado de los apartados

| N.º | Apartado | Estado |
| --- | --- | --- |
| 1 | Portada | Pendiente de completar con la identificación final. |
| 2 | Integrantes | Pendiente de confirmar nombres completos. |
| 3 | Descripción del problema | Redactado en el primer bloque. |
| 4 | Descripción general de la solución | Redactado; revisar alcance al integrar servicios y ventanillas. |
| 5 | Arquitectura utilizada | Redactado; contrastar después de la integración. |
| 6 | Diagrama general de componentes | Preparado, con lectura y archivo editable. |
| 7 | Diseño de la base de datos | Preparado: diez tablas, once relaciones y sus reglas; revisar tras integrar servicios/ventanillas. |
| 8 | Descripción de los patrones utilizados | Pendiente. |
| 9 | Explicación del mecanismo de comunicación | Pendiente; fuente disponible en código y guías. |
| 10 | Explicación del uso de hilos | Pendiente. |
| 11 | Explicación del control de concurrencia | Pendiente; integrar reglas y evidencias finales. |
| 12 | Instrucciones para ejecutar el sistema | Pendiente de resumir la guía existente. |
| 13 | Distribución del trabajo realizado por cada integrante | Pendiente de contrastar los aportes reales. |
| 14 | Conclusiones | Se redactarán al cerrar la integración. |

## Orden de continuación

1. **Problema, solución y arquitectura:** apartados 3 a 5, preparados en el borrador actual.
2. **Diagramas y datos:** apartados 6 y 7, preparados en [diagramas](diagramas/README.md), con archivos editables y lectura de conexiones.
3. **Decisiones técnicas:** apartados 8 a 11, con ejemplos del código y sus motivos.
4. **Ejecución y comprobación:** apartado 12 y escenario de demostración, reutilizando los procedimientos existentes.
5. **Identificación y cierre:** apartados 1, 2, 13 y 14.
6. **Presentación y defensa:** material visual, recorrido y explicación individual.
7. **Paquete de entrega:** revisión de archivos, scripts, instrucciones y ausencia de secretos.

Los diagramas requeridos son el general de componentes y el de base de datos. Un UML de clases es material complementario; no reemplaza ninguno de ellos.

## Fuentes que ya existen

- [Descripción y estado del sistema](../README.md).
- [Instalación en NetBeans](../INSTALACION.md).
- [Configuración de red y TLS](../RED_Y_TLS.md).
- [Esquema actual](../database/script%20borrador.sql), [datos iniciales](../database/datos_prueba.sql) y [migraciones](../database/migraciones).
- [Acceso](../database/INICIALIZAR_ACCESO.md), [Personas](../database/ADMINISTRAR_PERSONAS.md), [Asignaciones](../database/CONFIGURAR_ATENCION.md) y [rellamado](../database/RELLAMADO.md).
- [Procedimientos de concurrencia](../pruebas/README.md) y [verificación de instalación limpia](../pruebas/QA_instalacion_2026-11-12.md).

Cada informe QA conserva el alcance de su versión. Los casos repetidos de regresión no se suman como escenarios nuevos. El ensayo entre computadoras y la medición con la base remota siguen pendientes. La administración de servicios y ventanillas se documentará como integrada cuando se reciba y compruebe el bloque de Joshua.
