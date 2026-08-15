# BRIKA — DEFINITIVE WORKFLOW & STATUS SPECIFICATION V1

## 1. Objetivo

Este documento congela el workflow funcional de una operación hipotecaria en Brika V1 y define los estados, transiciones, reglas y efectos principales.

Los códigos técnicos son estables aunque el nombre visible pueda cambiar mediante i18n.

---

# 2. Estados de CASE

| Código | Nombre | Terminal | Descripción |
|---|---|---:|---|
| PRESTUDY | Preestudio | No | Operación inicial en evaluación |
| DOCUMENTATION | Documentación | No | Recopilación y validación documental |
| ANALYSIS | Análisis | No | Análisis financiero, cliente, inmueble y operación |
| BANK_SEARCH | Búsqueda bancaria | No | Selección de entidades objetivo |
| BANK_SUBMISSION | Enviada a bancos | No | Solicitudes enviadas a entidades |
| BANK_REVIEW | En revisión bancaria | No | Entidades estudiando la operación |
| OFFER | Oferta | No | Existe una o varias propuestas/ofertas |
| FORMALIZATION | Formalización | No | Operación en proceso de firma |
| COMPLETED | Completada | Sí | Operación finalizada correctamente |
| CANCELLED | Cancelada | Sí | Operación cerrada sin formalización |

---

# 3. Transiciones permitidas

## PRESTUDY

Puede pasar a:
- DOCUMENTATION
- CANCELLED

## DOCUMENTATION

Puede pasar a:
- ANALYSIS
- PRESTUDY
- CANCELLED

## ANALYSIS

Puede pasar a:
- DOCUMENTATION
- BANK_SEARCH
- CANCELLED

## BANK_SEARCH

Puede pasar a:
- ANALYSIS
- BANK_SUBMISSION
- CANCELLED

## BANK_SUBMISSION

Puede pasar a:
- BANK_REVIEW
- BANK_SEARCH
- CANCELLED

## BANK_REVIEW

Puede pasar a:
- BANK_SUBMISSION
- BANK_SEARCH
- OFFER
- CANCELLED

## OFFER

Puede pasar a:
- BANK_REVIEW
- FORMALIZATION
- CANCELLED

## FORMALIZATION

Puede pasar a:
- COMPLETED
- OFFER
- CANCELLED

## COMPLETED

No permite transición normal.

Cualquier reapertura futura deberá ser una acción explícita y auditada, no un cambio de estado ordinario.

## CANCELLED

No permite transición normal.

Una reapertura futura deberá ser una acción explícita y auditada.

---

# 4. Reglas de transición

Toda transición deberá:

1. comprobar estado actual;
2. comprobar permiso;
3. comprobar tenant;
4. comprobar precondiciones funcionales;
5. registrar estado anterior;
6. registrar estado nuevo;
7. registrar usuario/sistema;
8. registrar timestamp;
9. registrar motivo/comentario cuando corresponda;
10. publicar evento de dominio.

---

# 5. Precondiciones recomendadas

## PRESTUDY → DOCUMENTATION

Debe existir:
- cliente;
- operación válida;
- información mínima de contacto.

## DOCUMENTATION → ANALYSIS

Debe existir la documentación mínima configurada para el tipo de operación o una excepción autorizada.

## ANALYSIS → BANK_SEARCH

Debe existir información suficiente para realizar el análisis.

## BANK_SEARCH → BANK_SUBMISSION

Debe existir al menos una entidad objetivo o una estrategia de búsqueda válida.

## BANK_REVIEW → OFFER

Debe existir al menos una respuesta/oferta compatible registrada.

## OFFER → FORMALIZATION

Debe existir una oferta seleccionada o una decisión equivalente autorizada.

## FORMALIZATION → COMPLETED

Debe cumplirse el checklist de formalización definido para el tipo de operación.

Las precondiciones deben ser configurables cuando dependan del producto, empresa o tipo de operación.

---

# 6. Cancelación

Cualquier estado no terminal podrá pasar a CANCELLED si el usuario tiene permiso.

Debe registrarse:
- motivo obligatorio;
- usuario;
- fecha;
- comentario opcional;
- origen de la cancelación.

Motivos iniciales de catálogo:
- CLIENT_REQUEST;
- INELIGIBLE;
- NO_FINANCING;
- PROPERTY_ISSUE;
- DUPLICATE;
- ABANDONED;
- OTHER.

---

# 7. Estado publicado al Portal Cliente

El estado interno de CASE y el estado mostrado al cliente son conceptos distintos.

Ejemplo:

CASE interno:
`BANK_REVIEW`

Estado publicado:
`EN_REVISION`

El broker podrá publicar un estado seguro y comprensible para el cliente sin exponer información interna.

---

# 8. Eventos

Eventos conceptuales:

- CaseCreated
- CaseStatusChanged
- CaseCancelled
- CaseCompleted
- CaseReopened
- BankSubmissionCreated
- BankResponseReceived
- OfferCreated
- FormalizationStarted

Los eventos podrán activar notificaciones, tareas o procesos asíncronos.

---

# 9. Reapertura

La reapertura no forma parte de las transiciones normales.

Si se habilita:
- requiere permiso específico;
- requiere motivo;
- queda auditada;
- genera `CaseReopened`;
- debe indicar estado destino.

---

# 10. Historial

`case_status_history` conservará como mínimo:

- id;
- case_id;
- previous_status;
- new_status;
- changed_by;
- changed_at;
- reason;
- metadata.

---

# 11. Regla de implementación

Los estados y transiciones no deben estar duplicados entre Angular y backend.

El backend es la autoridad.

Angular utilizará la información proporcionada por la API para mostrar acciones permitidas.

---

# 12. Aceptación

Una implementación cumple este documento cuando:
- sólo permite transiciones válidas;
- impide saltos no autorizados;
- registra el historial;
- aplica precondiciones;
- respeta permisos y tenant;
- mantiene separado el estado interno del estado publicado al cliente.
