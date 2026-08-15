# BRIKA — DATA GOVERNANCE SPECIFICATION V1

## 1. Objetivo

Garantizar que los datos utilizados por Brika sean trazables, consistentes y comprensibles.

## 2. Procedencia

Los datos relevantes deberán poder identificar:
- fuente;
- actor;
- fecha;
- método de obtención;
- evidencia asociada.

## 3. Evidencias

Cuando un dato sea relevante para análisis hipotecario, podrá asociarse a un documento o evidencia.

Ejemplo conceptual:

INGRESO DECLARADO
→ FUENTE: CLIENT
→ EVIDENCIA: NÓMINA
→ FECHA
→ DOCUMENT VERSION

## 4. Historial

Los cambios importantes deben conservar histórico suficiente para reconstruir cómo se obtuvo un resultado.

## 5. Datos derivados

Los valores calculados deberán distinguirse de los datos declarados.

Ejemplos:
- DTI calculado;
- LTV calculado;
- scoring;
- cuota estimada.

## 6. Perfil financiero

El perfil financiero no se debe tratar como un simple formulario editable sin control.

Los cambios relevantes deben quedar trazables.

## 7. IA

La IA no puede convertirse en fuente silenciosa de verdad.

Los datos extraídos por IA deben poder identificar su origen y, cuando corresponda, requerir validación.

## 8. Calidad

El sistema deberá distinguir:
- dato confirmado;
- dato pendiente;
- dato estimado;
- dato rechazado;
- dato desactualizado.

## 9. Principio

La procedencia forma parte del valor del dato.
