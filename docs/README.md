# Documentación del proyecto

El [documento técnico editable en Word](documento_tecnico.docx) desarrolla los catorce apartados solicitados: portada, integrantes, explicación del sistema, distribución del trabajo y conclusiones. Conserva las limitaciones y comprobaciones pendientes de la integración.

El [documento técnico en Markdown](documento_tecnico.md) conserva una explicación ampliada de los apartados 3 a 12 como apoyo de consulta. El Word es la versión concentrada para la entrega académica. Los diagramas se conservan en [formatos editables y exportados](diagramas/README.md).

## Material del entregable

| Material | Ubicación |
| --- | --- |
| Enunciado original | [Proyecto de Programación III](enunciado/Proyecto_Programacion_III.pdf) |
| Documento técnico | [Word](documento_tecnico.docx) y [consulta en Markdown](documento_tecnico.md) |
| Componentes y base de datos | [Diagramas y lectura de conexiones](diagramas/README.md) |
| Creación y datos iniciales | [Esquema](../database/esquema.sql) y [datos de prueba](../database/datos_prueba.sql) |
| Actualización de bases existentes | [Migraciones](../database/migraciones) |
| Configuración y ejecución | [Instalación](../INSTALACION.md) y [red y TLS](../RED_Y_TLS.md) |
| Pruebas reproducibles | [Procedimientos de concurrencia](../pruebas/README.md) |

## Apartados del documento técnico

La sección 23.C del enunciado solicita catorce apartados:

| N.º | Apartado | Estado |
| --- | --- | --- |
| 1 | Portada | Incorporada en Word. |
| 2 | Integrantes | Incorporados en Word. |
| 3 | Descripción del problema | Redactado. |
| 4 | Descripción general de la solución | Redactado. |
| 5 | Arquitectura utilizada | Redactado. |
| 6 | Diagrama general de componentes | Preparado, con lectura y archivo editable. |
| 7 | Diseño de la base de datos | Preparado: diez tablas y once relaciones. |
| 8 | Descripción de los patrones utilizados | Redactado: MVC, Repository y publicación/suscripción. |
| 9 | Explicación del mecanismo de comunicación | Redactado: protocolo, TLS, sesiones y avisos. |
| 10 | Explicación del uso de hilos | Redactado. |
| 11 | Explicación del control de concurrencia | Redactado. |
| 12 | Instrucciones para ejecutar el sistema | Redactado, con instalación y demostración. |
| 13 | Distribución del trabajo realizado por cada integrante | Incorporada en Word a partir del historial de contribuciones. |
| 14 | Conclusiones | Incorporadas en Word, con el alcance y las verificaciones pendientes. |

Los apartados técnicos incluyen la administración de servicios y ventanillas integrada. Los diagramas conservan las cuatro aplicaciones y el modelo de diez tablas, que este bloque no modifica. La presentación sigue pendiente. El ensayo entre computadoras y el rendimiento remoto se mantienen como comprobaciones pendientes; ejecutar varios procesos en una sola computadora no los sustituye.

## Guías de operación

- [Acceso y sesiones](../database/INICIALIZAR_ACCESO.md).
- [Personas y credenciales](../database/ADMINISTRAR_PERSONAS.md).
- [Habilitaciones y asignaciones](../database/CONFIGURAR_ATENCION.md).
- [Servicios y ventanillas](../database/ADMINISTRAR_CATALOGO.md).
- [Rellamado](../database/RELLAMADO.md).

Las configuraciones privadas, los almacenes TLS y las exportaciones con credenciales no forman parte del entregable. Para una instalación nueva se utilizan el esquema y los datos iniciales versionados.
