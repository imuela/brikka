# BRIKA — RABBITMQ SPECIFICATION V1

## 1. Uso

RabbitMQ se utilizará para procesos asíncronos y eventos cuando sea necesario.

No utilizar mensajería para operaciones CRUD simples que necesiten respuesta inmediata.

## 2. Eventos principales

- case.status.changed
- document.uploaded
- document.request.created
- bank.request.created
- bank.response.received
- notification.requested
- ai.document.analysis.requested

### Notificaciones por canal (`ADR-NOTIF-001`)

`notification.requested` puede generar entregas (`notification_deliveries`) en varios canales a la vez. V1 solo tiene workers para `IN_APP` y `EMAIL`; `PUSH`/`SMS` son valores de catálogo válidos sin worker conectado en V1.

Cada worker de canal escribe su propio estado en `notification_deliveries` tras procesar el mensaje. No se publican eventos adicionales de éxito/fallo en V1 (el estado se consulta vía `GET /notifications/{id}/deliveries`); si en el futuro se necesita reaccionar a fallos de entrega, se añadirán `notification.delivery.failed` mediante un ADR propio.

### Actividad funcional (`ADR-AUDIT-001`)

Los mismos eventos de dominio (`case.status.changed`, `document.uploaded`, etc.) son consumidos tanto por el escritor de `audit_events` como, de forma independiente, por el escritor de `activities`. Son dos consumidores distintos del mismo evento, no una relación de dependencia entre las dos tablas.

### Python Worker (`ADR-AI-001`)

`ai.document.analysis.requested` es consumido por el Python Worker. El worker **no** publica el resultado de vuelta a RabbitMQ ni escribe en PostgreSQL: entrega el resultado mediante una llamada síncrona a un endpoint interno de Spring Boot (fuera de `/api/v1` público), que lo valida y persiste en `document_extractions`. Esto mantiene el principio "Python Worker → PostgreSQL PROHIBIDO" sin depender de que el mensaje de respuesta se procese correctamente.

## 3. Envelope

```json
{
  "eventId": "uuid",
  "eventType": "document.uploaded",
  "occurredAt": "...",
  "companyId": "uuid",
  "aggregateType": "DOCUMENT",
  "aggregateId": "uuid",
  "payload": {}
}
```

## 4. Idempotencia

Los consumidores deben tolerar mensajes duplicados.

`eventId` debe poder utilizarse para deduplicación.

## 5. Retries

- retry limitado;
- backoff;
- dead-letter queue.

## 6. Outbox

Los eventos críticos derivados de cambios transaccionales deberán considerar patrón Transactional Outbox para evitar inconsistencias BD/event bus.

## 7. Seguridad

- credenciales fuera del código;
- TLS;
- usuarios con mínimos privilegios;
- colas separadas por responsabilidad.

## 8. Regla

Un mensaje no debe permitir saltarse autorización. El consumidor vuelve a comprobar contexto cuando la operación sea sensible.
