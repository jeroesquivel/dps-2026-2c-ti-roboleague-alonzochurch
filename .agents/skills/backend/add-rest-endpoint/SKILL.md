---
name: add-rest-endpoint
description: Agrega o modifica endpoints REST de RoboLeague con Spring Boot para exponer un flujo especificado mediante puertos de entrada, DTOs web y pruebas de API. No usar para inventar funcionalidades o cambiar reglas del core.
---

# Agregar un endpoint REST

Implementa la adaptación HTTP de una spec sin trasladar reglas de negocio a la web. Antes de
cambiar código, leer `architecture.md`, la spec del flujo, los puertos de entrada involucrados y
sus tests. Resolver primero cualquier pregunta abierta que cambie el contrato HTTP.

## Recorrido

```text
HTTP request
-> request DTO
-> mapper
-> command o identificador tipado
-> puerto de entrada
-> resultado del core
-> mapper
-> response DTO
-> HTTP response
```

El controller depende de la interfaz del puerto de entrada, nunca del `*UseCase`, de un repositorio
o de un adaptador concreto. No consulta almacenamiento, no decide reglas y no devuelve agregados del
dominio directamente.

Mantener DTOs de request y response propios del adaptador. No agregar anotaciones Spring, Jackson o
Jakarta al core ni modificar value objects para facilitar la serialización. El actor de auditoría
puede construirse desde el dato recibido en la petición; esta etapa no requiere autenticación.

Registrar casos de uso y colaboradores como beans en la configuración Spring. Los repositorios en
memoria son instancias compartidas durante la ejecución. Inyectar `Clock` e `IdGenerator`; no
obtenerlos globalmente.

## Errores

Usar el manejador HTTP compartido para traducir la jerarquía del dominio. Mantener uniforme el
formato de error y el criterio definido por el contrato de la API. No capturar excepciones en cada
controller salvo que la spec exija una representación distinta. Los códigos HTTP pertenecen al
contrato web, no al dominio.

## Pruebas y finalización

- Cubrir los criterios de aceptación que atraviesan HTTP: request y response exitosos, formato
  inválido, ausencia, violación de regla y conflicto cuando correspondan.
- Probar el controller y la configuración web real; no reemplazar toda la integración con mocks.
- Reutilizar adaptadores en memoria, reloj fijo e identificadores deterministas para recorridos
  reproducibles.
- No crear endpoints, consultas o campos “por si acaso”. Si falta un puerto necesario cuyo
  significado no está definido por la spec, detener la implementación y explicitar la decisión.
- Ejecutar `mvn verify`.
- Actualizar el contrato de API y `README.md` cuando cambie el contrato público. Actualizar
  `DESIGN.md` sólo si cambió una decisión de diseño.
