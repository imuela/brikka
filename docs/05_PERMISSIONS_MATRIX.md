# BRIKA — PERMISSIONS MATRIX V1

> **Nota:** documento conceptual **histórico**. El catálogo definitivo de permisos es `14_DEFINITIVE_PERMISSION_CATALOG.md`.

## 1. Roles

- SUPERADMIN
- MANAGER
- BROKER
- CLIENT

## 2. Principio

El rol determina capacidades generales. La autorización final debe considerar tenant, recurso, relación con la operación y visibilidad.

## 3. Matriz conceptual

| Recurso | SUPERADMIN | MANAGER | BROKER | CLIENT |
|---|---|---|---|---|
| Companies | CRUD plataforma | lectura limitada | no | no |
| Users | administración plataforma | gestionar empresa | limitado | no |
| Clients | soporte autorizado | CRUD empresa | CRUD asignado/autorizado | propios datos permitidos |
| Cases | soporte autorizado | CRUD empresa | CRUD asignado/autorizado | consulta publicada |
| Documents | soporte autorizado | gestión empresa | gestión autorizada | propios/publicados |
| Banks | global/configuración | consulta/gestión autorizada | uso operativo | no |
| Offers | soporte | gestión empresa | gestión operativa | sólo publicación autorizada |
| Tasks | soporte | CRUD empresa | CRUD operativa | no |
| Conversations | soporte | empresa | operativas | propias/autorizadas |
| Scoring | configuración global | uso/configuración autorizada | uso | no por defecto |
| Audit | plataforma | empresa | limitado | limitado a actividad propia |
| AI | plataforma | autorizado | autorizado | sólo funciones expresamente habilitadas |

## 4. Regla

La matriz es una base funcional. Los permisos atómicos definitivos se definirán como catálogo de permissions.

## 5. Cliente

CLIENT nunca obtiene por defecto permisos internos por pertenecer a una operación.
