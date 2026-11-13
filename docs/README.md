# Documentación del proyecto

Documento editable en Word: [borrador del documento técnico](Documento_tecnico_Gestion_de_Turnos_APA7_borrador.docx).

El Word incluye la portada institucional y los integrantes confirmados, con el cuerpo, las tablas, las figuras y las referencias adaptados a APA 7. El [documento técnico en Markdown](documento_tecnico.md) conserva el desarrollo de los apartados 3 a 12. Los aportes y las conclusiones siguen pendientes del cierre de la integración.

Este trabajo sigue los catorce apartados de la sección 23.C del enunciado. El borrador se completa por bloques; la versión de referencia inicial es `cc9405e`.

## Estado de los apartados

| N.º | Apartado | Estado |
| --- | --- | --- |
| 1 | Portada | Preparada en Word: Universidad Nacional, Sede Regional Brunca, Campus Coto; EIF206 Programación III, Grupo 95 (NRC 50891); Juan Gamboa Abarca; 13 de noviembre de 2026. |
| 2 | Integrantes | Incorporados en Word: John Perez Quesada, Joe Sandoval Diaz y Joshua Barrantes Granados. |
| 3 | Descripción del problema | Redactado en el primer bloque. |
| 4 | Descripción general de la solución | Redactado; revisar alcance al integrar servicios y ventanillas. |
| 5 | Arquitectura utilizada | Redactado; contrastar después de la integración. |
| 6 | Diagrama general de componentes | Preparado, con lectura y archivo editable. |
| 7 | Diseño de la base de datos | Preparado: diez tablas, once relaciones y sus reglas; revisar tras integrar servicios/ventanillas. |
| 8 | Descripción de los patrones utilizados | Redactado: MVC, Repository y publicación/suscripción. |
| 9 | Explicación del mecanismo de comunicación | Redactado: protocolo, TLS, sesión, avisos y confirmaciones inciertas. |
| 10 | Explicación del uso de hilos | Redactado: servidor, clientes, JavaFX y recursos compartidos. |
| 11 | Explicación del control de concurrencia | Redactado; contrastar la integración de servicios/ventanillas. |
| 12 | Instrucciones para ejecutar el sistema | Redactado: instalación, NetBeans, demostración y recuperación; revalidar al integrar servicios/ventanillas. |
| 13 | Distribución del trabajo realizado por cada integrante | Pendiente de contrastar los aportes reales. |
| 14 | Conclusiones | Se redactarán al cerrar la integración. |

## Orden de continuación

1. **Problema, solución y arquitectura:** apartados 3 a 5, preparados en el borrador actual.
2. **Diagramas y datos:** apartados 6 y 7, preparados en [diagramas](diagramas/README.md), con archivos editables y lectura de conexiones.
3. **Decisiones técnicas:** apartados 8 a 11 redactados, con ejemplos del código y sus motivos; contrastar tras integrar a Joshua.
4. **Ejecución y comprobación:** apartado 12 redactado; escenario propuesto y referencias a las pruebas existentes.
5. **Cierre:** portada e integrantes preparados; completar los apartados 13 y 14 después de contrastar los aportes y el resultado final.
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

## Contraste de la base exportada

La [revisión de la exportación](revision_exportacion_bd.md) encontró coincidencia de tablas y relaciones, pero ausencia de CHECK y opciones de varias tablas. La exportación contiene credenciales y no se incorpora al repositorio. El esquema versionado sigue siendo la referencia del entregable.

Los datos de portada e integrantes ya están confirmados. Se conservaron los nombres tal como aparecen en la identificación del grupo. No se atribuyen aportes definitivos ni se redactan conclusiones de cierre antes de integrar el trabajo restante.

