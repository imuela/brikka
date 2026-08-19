# BRIKA — BANK ENGINE DETAILED V1

## 1. Separación

`BANK` = entidad bancaria global.

`BANK_CONTACT` = contacto operativo de una empresa con esa entidad.

`BANK_CRITERIA_VERSION` = reglas/criterios conocidos.

`BANK_REQUEST` = interacción concreta.

`BANK_RESPONSE` = respuesta.

`BANK_OFFER` = propuesta.

`FINAL_FINANCING` = resultado final.

## 2. Matching

El motor recibe un snapshot autorizado de:
- cliente;
- operación;
- inmueble;
- financiación.

Evalúa criterios versionados.

## 3. Resultado

Cada regla debe poder producir:
- PASS;
- FAIL;
- WARNING;
- NOT_EVALUATED.

El resultado final debe explicar qué reglas llevaron a la clasificación.

## 4. No elegibilidad

Una entidad puede resultar no elegible por una regla crítica.

La explicación se conservará.

## 5. Contacto

El usuario puede seleccionar uno de los contactos disponibles de su empresa para el banco.

La consulta se filtra por `company_id`.

## 6. Historial

`BANK_REQUEST.contact_snapshot` conserva el contexto del contacto usado en el momento del envío.

## 7. Overrides

Un override requiere:
- permiso;
- motivo;
- usuario;
- timestamp;
- valor anterior;
- valor nuevo.

## 8. Reglas

No codificar criterios bancarios dispersos por controllers/services.

Deben existir en una capa de dominio/configuración versionada.

## 9. IA

La IA puede ayudar a interpretar información, pero el matching determinista debe permanecer reproducible.
