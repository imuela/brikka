# BRIKA — TESTING SPECIFICATION V1

## Pirámide

- unit tests;
- integration tests;
- API tests;
- security tests;
- end-to-end tests.

## Obligatorio

Toda funcionalidad nueva deberá incluir pruebas.

## Tenant isolation

Se probará explícitamente que:
- empresa A no puede acceder a empresa B;
- usuarios no autorizados reciben rechazo;
- CLIENT no puede acceder a recursos internos.

## Documentos

Probar:
- subida;
- versión;
- aprobación;
- rechazo;
- acceso;
- descarga autorizada;
- descarga no autorizada.

## Portal Cliente

Probar visibilidad positiva y negativa.

## API

Probar:
- validación;
- autorización;
- errores;
- paginación;
- idempotencia donde corresponda.

## Regresión

Cada release debe ejecutar la suite correspondiente antes del despliegue.
