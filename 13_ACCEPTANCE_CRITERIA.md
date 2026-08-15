# BRIKA — ACCEPTANCE CRITERIA V1

## Plataforma
- La aplicación funciona por tenant.
- Los tenants están aislados.
- Los roles y permisos se aplican correctamente.

## Clientes
- Se pueden crear y gestionar clientes.
- Los clientes sólo son accesibles dentro del tenant.

## Operaciones
- Se pueden crear operaciones.
- Se pueden asociar participantes.
- Los cambios de estado son trazables.

## Documentación
- Se pueden solicitar documentos.
- Se pueden subir.
- Se versionan.
- Se pueden aprobar/rechazar.
- Los accesos están autorizados.

## Portal Cliente
- El cliente puede autenticarse.
- Sólo ve sus operaciones.
- Sólo ve información publicada.
- Puede subir documentación autorizada.
- Puede comunicarse según permisos.

## Financiación
- Se pueden crear simulaciones.
- Se pueden registrar solicitudes bancarias.
- Se pueden registrar y comparar ofertas.
- Se diferencia oferta de financiación final.

## Scoring
- El score es reproducible.
- Existe desglose.
- Se conserva el histórico.

## Seguridad
- No existe acceso horizontal entre tenants.
- CLIENT no accede a información interna.
- Las acciones sensibles quedan auditadas.

## Calidad
- Funcionalidades críticas tienen tests.
- Las migraciones son reproducibles.
- El proyecto puede desplegarse de forma automatizada.

## Plataforma (planes/entitlements)
- SUPERADMIN puede crear/gestionar planes y entitlements.
- Una empresa tiene una suscripción activa a un plan.
- Una funcionalidad limitada por plan se rechaza si falta el entitlement, incluso con el permiso RBAC presente.
- `companies.status` y `company_subscriptions.status` son independientes y ninguna acción los confunde.

## Documentación (requirements)
- El sistema puede determinar automáticamente la documentación necesaria de una operación a partir de `document_requirements`.
- Una `document_request` conserva de qué `document_requirement` se originó, cuando aplica.
- No existe tabla `files` independiente; los metadatos de fichero están en `document_versions`.

## Comunicaciones
- Una conversación tipo CLIENT nunca es accesible por un cliente que no sea `conversation_participant` de esa conversación.
- Los mensajes pueden llevar adjuntos con las mismas reglas de seguridad que los documentos.

## Notificaciones
- V1 entrega notificaciones por `IN_APP` y `EMAIL`.
- El estado de entrega se puede consultar por canal (`notification_deliveries`).
- No existe ningún proveedor `PUSH`/`SMS` conectado en V1.

## Actividad y auditoría
- Existe un timeline de actividad funcional (`activities`) distinto del log de auditoría (`audit_events`).
- El acceso a `activities` no requiere `AUDIT_READ`.

## IA / Python Worker
- El Python Worker no tiene credenciales ni conectividad de red hacia PostgreSQL.
- Los resultados de OCR/extracción solo se persisten a través de Spring Boot.
