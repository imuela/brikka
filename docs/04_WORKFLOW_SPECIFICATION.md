# BRIKA — WORKFLOW SPECIFICATION V1

> **Nota:** documento conceptual **histórico**. El catálogo definitivo de estados/transiciones es `13_DEFINITIVE_WORKFLOW_SPECIFICATION.md`.

## 1. Principio

Los workflows de negocio estarán separados de las tareas y de las solicitudes documentales.

## 2. Workflow principal

CAPTACIÓN
→ PREESTUDIO
→ DOCUMENTACIÓN
→ ANÁLISIS
→ BÚSQUEDA DE FINANCIACIÓN
→ ENVÍO A ENTIDADES
→ RESPUESTAS/OFERTAS
→ SELECCIÓN
→ FORMALIZACIÓN
→ CIERRE

Los nombres definitivos de estados serán un catálogo versionado.

## 3. Transiciones

Una transición debe:
- partir de un estado válido;
- estar permitida;
- registrar actor;
- registrar fecha;
- conservar estado anterior y nuevo;
- generar eventos cuando corresponda.

## 4. Workflows independientes

### Case Workflow
Controla el ciclo de vida de la operación.

### Document Workflow
Controla solicitud, recepción, revisión y sustitución.

### Bank Workflow
Controla envío, seguimiento, respuesta y oferta.

### Client Portal Publication Workflow
Controla qué información pasa de interna a visible.

### Task Workflow
Controla trabajo operativo.

## 5. TASK ≠ DOCUMENT_REQUEST

Una solicitud documental puede generar una tarea, pero son conceptos independientes.

## 6. Publicación al cliente

La información interna no pasa automáticamente al Portal Cliente por el hecho de existir.

La publicación debe ser explícita y autorizada.

## 7. Automatizaciones

Los eventos podrán activar:
- notificaciones;
- tareas;
- solicitudes documentales;
- integraciones;
- procesos de IA autorizados.

Las automatizaciones deben ser auditables.

## 8. Cancelación

Las operaciones canceladas conservarán historial y trazabilidad. No se eliminarán silenciosamente para evitar pérdida de información.
