---
name: implement-flow
description: Implementa en el frontend un flujo funcional especificado de RoboLeague consumiendo la API existente, con la interfaz mínima necesaria y sin duplicar reglas del core. No usar para diseñar endpoints todavía inexistentes.
---

# Implementar un flujo de frontend

Leer la spec funcional y el contrato HTTP implementado antes de modificar la interfaz. La spec
define el comportamiento visible; la API define los datos disponibles. No inferir ni inventar un
contrato a partir de las clases Java internas.

Organizar el código dentro de la feature correspondiente y reutilizar el cliente HTTP, componentes
y convenciones existentes. Implementar sólo las pantallas, formularios, estados y navegación
necesarios para completar el flujo solicitado.

El frontend puede validar presencia y formato para mejorar la experiencia, presentar estados,
errores y explicaciones devueltos por la API, y conservar estado propio de la interfaz.

El frontend no puede:

- recalcular puntajes, rankings o desempates;
- decidir elegibilidad o transiciones de apelaciones;
- duplicar reglas del reglamento;
- acceder al almacenamiento del backend;
- inventar endpoints o campos no definidos;
- incorporar autenticación no solicitada.

Representar de forma visible los estados exigidos por la spec, especialmente operaciones
pendientes, rechazos, conflictos y explicaciones de puntaje. No ocultar errores de negocio detrás
de mensajes genéricos si la API proporciona una explicación utilizable.

Cubrir el flujo exitoso y los estados de error visibles indicados en los criterios de aceptación.
Ejecutar los comandos de test y build definidos por el frontend antes de terminar. Si todavía no
existen esas herramientas o el endpoint requerido, informarlo en lugar de fabricar una alternativa.
