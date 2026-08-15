# BRIKA — AI V1 SCOPE

## 1. Objetivo

La IA es asistente del broker, no autoridad de negocio.

## 2. Casos V1

### A. Extracción documental
Extraer datos estructurados de documentos.

Resultado:
- campo;
- valor;
- confianza;
- fuente;
- página/fragmento cuando esté disponible.

### B. Resumen de operación
Generar resumen para el broker utilizando datos autorizados.

### C. Explicación
Explicar scoring y criterios utilizando resultados deterministas.

### D. Borradores
Generar borradores de comunicaciones.

El envío final requiere acción/autorización humana.

## 3. No hacer en V1

- aprobar hipotecas;
- decidir automáticamente qué banco debe aceptar una operación;
- enviar comunicaciones sensibles sin autorización;
- modificar datos financieros definitivos sin validación;
- acceso SQL directo;
- acceso indiscriminado a todos los documentos del tenant.

## 4. Arquitectura

Frontend/backend
→ AI Application Service
→ AI Gateway/Orchestrator
→ tools autorizadas
→ proveedor/modelo

Para extracción documental/OCR, el Gateway despacha el trabajo al **Python Worker** (`ADR-AI-001`) vía RabbitMQ (`ai.document.analysis.requested`). El worker es stateless, sin acceso ni credenciales de PostgreSQL, aislado a nivel de red, y entrega el resultado exclusivamente a un endpoint interno de Spring Boot que lo persiste en `document_extractions`. El worker nunca escribe en PostgreSQL directamente.

## 5. Tools

Ejemplos:
- get_case_summary
- get_client_financial_profile
- get_document_metadata
- get_scoring_result
- get_bank_criteria
- draft_client_message

Cada tool aplica autorización y tenant.

## 6. RAG

Sólo para información documental aprobada y dentro del alcance autorizado.

Ningún caso de uso RAG/embeddings está aprobado por defecto en V1 (`ADR-AI-001`). `pgvector` queda disponible como extensión de PostgreSQL para cuando se apruebe expresamente uno.

## 7. Privacidad

Minimización de datos.

No enviar al proveedor más información de la necesaria.

## 8. Auditoría

Registrar:
- usuario;
- caso;
- operación;
- modelo;
- proveedor;
- consumo;
- timestamp.

## 9. Validación humana

Los resultados críticos deben poder ser revisados y corregidos por un usuario autorizado.
