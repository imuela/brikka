# BRIKA — BANK ENGINE SPECIFICATION V1

## 1. Objetivo

Centralizar y hacer reproducible el análisis de compatibilidad entre una operación y las condiciones conocidas de las entidades financieras.

## 2. Catálogo global de bancos

`BANK` es un catálogo global y único de Brika.

Un banco no se duplica por empresa ni por broker.

Ejemplos:
- Santander
- Ibercaja
- BBVA
- CaixaBank

La información global del banco puede incluir:
- identidad;
- marca;
- datos corporativos;
- productos;
- criterios;
- configuraciones globales.

## 3. Contactos bancarios

Los contactos NO pertenecen al banco como catálogo global.

Un contacto bancario pertenece a una `COMPANY` de Brika y está asociado a un `BANK`.

Modelo conceptual:

`COMPANY 1 ─── N BANK_CONTACT N ─── 1 BANK`

Por tanto, cada empresa mantiene sus propios contactos para cada banco.

Una empresa puede tener:
- varios contactos del mismo banco;
- contactos de diferentes bancos;
- contactos compartidos entre sus brokers;
- contactos privados dentro de la empresa si se habilita esa visibilidad.

Un broker de otra empresa nunca puede consultar esos contactos.

## 4. Propiedad y visibilidad

La propiedad del contacto es siempre de la empresa.

V1 utilizará como mínimo:

- `COMPANY`: visible a usuarios autorizados de la empresa.
- `PRIVATE`: opcionalmente visible sólo al usuario propietario dentro de la empresa.

El contacto nunca se considerará un recurso global de Brika.

Si en el futuro existen contactos globales/oficiales mantenidos por SUPERADMIN, deberán modelarse como una categoría distinta y no mezclarse con los contactos privados de las empresas.

## 5. Datos de BANK_CONTACT

Como mínimo:

- id;
- company_id;
- bank_id;
- owner_user_id, si se utiliza visibilidad privada;
- name;
- position;
- department;
- branch;
- email;
- phone;
- secondary_phone;
- notes;
- visibility;
- active;
- created_at;
- updated_at.

## 6. Solicitudes bancarias

Una `BANK_REQUEST` pertenece a una operación y a un banco.

Opcionalmente puede indicar el `BANK_CONTACT` utilizado para la gestión.

Ejemplo:

`CASE → BANK_REQUEST → BANK → BANK_CONTACT`

La solicitud debe conservar la referencia al contacto seleccionado.

## 7. Historial

Si un contacto cambia posteriormente de:
- nombre;
- cargo;
- teléfono;
- correo;
- empresa/departamento;

la información histórica utilizada en una solicitud no debe quedar alterada de forma silenciosa.

La implementación podrá utilizar snapshot de los datos relevantes del contacto en `BANK_REQUEST` o una estrategia equivalente de versionado.

## 8. Motor determinista

Las reglas deterministas se ejecutarán mediante un motor controlado por configuración y versiones.

No dependerán exclusivamente de IA.

## 9. Criterios

Podrán contemplar, según información disponible:
- ingresos;
- estabilidad;
- antigüedad;
- endeudamiento;
- LTV;
- ahorro;
- edad;
- tipo de inmueble;
- finalidad;
- perfil profesional;
- garantías;
- otros criterios definidos.

## 10. Versionado

Las reglas bancarias deberán poder versionarse.

Un análisis histórico debe poder identificar qué versión de criterios utilizó.

## 11. Overrides

Los overrides manuales estarán permitidos únicamente para usuarios autorizados.

Deben registrar:
- valor anterior;
- nuevo valor;
- usuario;
- fecha;
- motivo;
- regla afectada.

## 12. Resultado

El motor podrá producir:
- entidades compatibles;
- entidades no compatibles;
- motivos;
- condiciones;
- advertencias.

## 13. Separación de responsabilidades

El motor bancario no aprueba una hipoteca. Ayuda al broker a priorizar y analizar opciones.

## 14. Auditoría

Los resultados y cambios de reglas relevantes deben ser trazables.

## 15. Regla de aislamiento

`BANK` es global.

`BANK_CONTACT` está aislado por `COMPANY`.

El backend debe comprobar siempre el tenant antes de permitir leer, modificar, seleccionar o eliminar un contacto.
