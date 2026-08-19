# BRIKA — FUNCTIONAL SPECIFICATION V1

**Documento:** FUNCTIONAL_SPECIFICATION.md  
**Versión:** 1.0  
**Estado:** En consolidación  
**Fuente:** BRIKA_MASTER_SPEC.md

---

## 1. Objetivo

Este documento define el comportamiento funcional de Brika V1.

Describe qué puede hacer cada tipo de usuario, qué módulos existen y cómo deben comportarse los principales flujos de negocio.

No define detalles técnicos de implementación. Esos detalles pertenecen a `TECHNICAL_SPECIFICATION.md`, `DATABASE_SPECIFICATION.md` y `API_SPECIFICATION.md`.

---

# 2. Roles funcionales

## 2.1 SUPERADMIN

Puede:

- gestionar empresas;
- gestionar planes;
- gestionar funcionalidades;
- consultar información de plataforma;
- gestionar catálogos globales;
- consultar auditoría de plataforma;
- gestionar configuración global.

No pertenece necesariamente a una empresa.

---

## 2.2 MANAGER

Puede, según permisos:

- gestionar usuarios de su empresa;
- gestionar brokers;
- consultar y gestionar clientes;
- consultar y gestionar operaciones;
- asignar operaciones;
- gestionar documentación;
- gestionar configuración de empresa;
- consultar reporting;
- administrar el Portal Cliente;
- consultar auditoría autorizada.

---

## 2.3 BROKER

Puede, según permisos:

- crear clientes;
- editar clientes;
- crear operaciones;
- gestionar titulares;
- gestionar inmuebles;
- gestionar documentación;
- realizar simulaciones;
- gestionar solicitudes de financiación;
- registrar respuestas bancarias;
- gestionar ofertas;
- crear tareas;
- comunicarse con clientes;
- utilizar funciones de scoring;
- utilizar funciones de IA autorizadas.

---

## 2.4 CLIENT

El cliente sólo podrá acceder a información correspondiente a su propia cuenta y a las operaciones que tenga autorizadas.

Puede:

- iniciar sesión;
- consultar operaciones publicadas;
- consultar estados publicados;
- consultar documentación visible;
- subir documentación;
- responder solicitudes;
- consultar mensajes;
- enviar mensajes;
- recibir notificaciones;
- modificar datos expresamente habilitados.

No podrá acceder a:

- notas internas;
- scoring interno no publicado;
- comunicaciones internas;
- información bancaria interna;
- otros clientes;
- otras empresas;
- documentación marcada como interna.

---

# 3. Dashboard

El sistema proporcionará dashboards adaptados al rol.

## Manager

Podrá visualizar, según permisos:

- operaciones activas;
- operaciones por estado;
- tareas pendientes;
- documentación pendiente;
- actividad reciente;
- indicadores de negocio;
- alertas.

## Broker

Podrá visualizar:

- operaciones asignadas;
- tareas pendientes;
- documentos pendientes;
- solicitudes bancarias;
- actividad reciente;
- mensajes.

## Client

Podrá visualizar:

- sus operaciones;
- estado publicado;
- documentación pendiente;
- solicitudes pendientes;
- mensajes;
- notificaciones.

---

# 4. Gestión de empresas

El SUPERADMIN podrá:

1. crear una empresa;
2. activar una empresa;
3. suspender una empresa;
4. cancelar una empresa;
5. consultar información de la empresa;
6. gestionar plan y suscripción;
7. consultar consumo y funcionalidades.

Cada empresa constituye un tenant independiente.

---

# 5. Gestión de usuarios

El MANAGER podrá:

1. invitar usuarios;
2. activar usuarios;
3. desactivar usuarios;
4. asignar roles;
5. modificar permisos cuando el modelo lo permita;
6. consultar actividad;
7. gestionar datos básicos.

El sistema deberá registrar las operaciones administrativas relevantes.

---

# 6. Gestión de clientes

## Crear cliente

El broker podrá registrar:

- nombre;
- apellidos;
- documento;
- fecha de nacimiento;
- nacionalidad;
- email;
- teléfono;
- dirección;
- situación laboral;
- otros datos definidos por el modelo funcional.

## Consultar cliente

La ficha permitirá consultar:

- datos personales;
- operaciones;
- documentación relacionada;
- actividad;
- comunicaciones autorizadas.

## Editar cliente

Los usuarios autorizados podrán modificar los datos permitidos.

Los cambios sensibles deberán quedar auditados.

## Eliminar cliente

La eliminación física no será el comportamiento predeterminado.

Se aplicará la política de conservación y eliminación definida en la especificación de seguridad y protección de datos.

---

# 7. Operaciones hipotecarias

Una operación (`CASE`) representa un expediente hipotecario.

## Crear operación

El broker podrá:

1. seleccionar o crear clientes;
2. indicar titulares;
3. introducir información inicial;
4. crear la operación;
5. asignarla a un broker.

## Estados

Los estados concretos se definirán en el catálogo oficial de estados.

Como principio, una operación podrá avanzar por etapas similares a:

- PRESTUDY
- DOCUMENTATION
- ANALYSIS
- BANK_SEARCH
- BANK_SUBMISSION
- BANK_REVIEW
- OFFER
- FORMALIZATION
- COMPLETED
- CANCELLED

El catálogo oficial y definitivo de estados, transiciones y precondiciones es `13_DEFINITIVE_WORKFLOW_SPECIFICATION.md`; esta lista es solo ilustrativa.

## Historial

Todo cambio de estado deberá conservar:

- estado anterior;
- estado nuevo;
- usuario que realizó el cambio;
- fecha;
- comentario cuando corresponda.

---

# 8. Titulares y participantes

Una operación podrá tener uno o varios clientes.

Roles funcionales:

- HOLDER;
- CO_HOLDER;
- GUARANTOR;
- OTHER.

El sistema deberá permitir:

- añadir participantes;
- modificar su rol;
- retirar participantes cuando la situación lo permita;
- consultar su relación con la operación.

---

# 9. Inmueble

Una operación podrá incluir información del inmueble.

Datos previstos:

- dirección;
- localidad;
- provincia;
- código postal;
- tipo de inmueble;
- precio;
- valor estimado;
- tasación;
- situación registral;
- otros datos relevantes.

La información inmobiliaria estará separada de los datos personales del cliente.

---

# 10. Simulación hipotecaria

El broker podrá crear simulaciones.

Una simulación podrá incluir:

- precio de compraventa;
- importe solicitado;
- porcentaje financiado;
- plazo;
- tipo de interés;
- cuota estimada;
- gastos considerados;
- aportación de fondos;
- otros parámetros.

Las simulaciones serán diferenciadas de la financiación finalmente concedida.

Una simulación no implica aprobación bancaria.

---

# 11. Documentación

## Requisitos

El sistema podrá determinar documentación necesaria para una operación.

Ejemplos:

- DNI;
- NIE;
- nóminas;
- IRPF;
- vida laboral;
- extractos;
- contrato laboral;
- contrato de arras;
- nota simple;
- tasación.

## Solicitud

El broker podrá solicitar un documento al cliente.

La solicitud podrá incluir:

- documento requerido;
- destinatario;
- mensaje;
- fecha límite;
- visibilidad;
- prioridad.

## Subida

El cliente o usuario autorizado podrá subir un archivo.

El sistema deberá:

1. validar el fichero;
2. almacenarlo;
3. crear una versión;
4. asociarlo al documento;
5. registrar quién lo subió;
6. registrar fecha y metadatos.

## Revisión

El broker podrá:

- aprobar;
- rechazar;
- solicitar una nueva versión.

Cuando rechace un documento deberá indicar el motivo cuando la configuración lo requiera.

---

# 12. Versionado documental

Un documento podrá tener múltiples versiones:

DOCUMENT
- VERSION 1
- VERSION 2
- VERSION 3

La versión actual estará claramente identificada.

Las versiones anteriores se conservarán conforme a la política de conservación.

---

# 13. Portal Cliente

## Acceso

El cliente tendrá autenticación propia.

Debe existir separación entre:

- autenticación interna;
- autenticación del Portal Cliente.

## Operaciones

El cliente verá únicamente operaciones autorizadas para su cuenta.

Podrá consultar:

- nombre o referencia publicada;
- estado publicado;
- progreso;
- información que el broker haya decidido exponer.

## Documentos

Podrá:

- ver documentos publicados;
- ver solicitudes pendientes;
- subir archivos;
- consultar estado;
- corregir documentación rechazada.

## Mensajería

Podrá:

- abrir conversaciones permitidas;
- enviar mensajes;
- responder;
- adjuntar archivos cuando esté permitido.

## Notificaciones

Podrá recibir notificaciones sobre:

- nuevas solicitudes;
- documentos rechazados;
- nuevos mensajes;
- cambios de estado publicados;
- otras acciones habilitadas.

---

# 14. Comunicación

Brika tendrá conversaciones.

Tipos funcionales:

- CLIENT;
- INTERNAL;
- SYSTEM.

Las conversaciones internas nunca serán visibles para CLIENT.

Los mensajes podrán contener adjuntos cuando el usuario tenga permiso.

---

# 15. Tareas

Los usuarios autorizados podrán crear tareas relacionadas con una operación.

Una tarea tendrá:

- título;
- descripción;
- responsable;
- prioridad;
- fecha límite;
- estado;
- fecha de finalización.

Estados previstos:

- TODO;
- IN_PROGRESS;
- BLOCKED;
- DONE;
- CANCELLED.

---

# 16. Bancos y financiación

Brika permitirá gestionar entidades financieras y solicitudes.

Flujo conceptual:

CASE
→ FINANCING REQUEST
→ BANK REQUEST
→ BANK RESPONSE
→ BANK OFFER
→ FINAL FINANCING

El sistema conservará el historial de comunicaciones y decisiones relevantes.

---

# 17. Ofertas

Una oferta bancaria podrá registrar, según información disponible:

- entidad;
- importe;
- plazo;
- tipo;
- condiciones;
- productos vinculados;
- cuota;
- gastos;
- observaciones;
- estado.

Las ofertas podrán compararse dentro de una operación.

---

# 18. Scoring

El sistema calculará puntuaciones según las reglas configuradas.

Se contemplan:

- client score;
- property score;
- operation score.

El resultado deberá ser explicable.

Ejemplo:

INGRESOS       +20
ESTABILIDAD    +10
AHORRO         +15
ENDEUDAMIENTO  -10
LTV             -5

El scoring no sustituye la decisión humana ni la aprobación bancaria.

---

# 19. Actividad

Brika mostrará un historial funcional de actividad.

Ejemplos:

- cliente creado;
- operación creada;
- documento subido;
- documento aprobado;
- documento rechazado;
- estado cambiado;
- mensaje enviado;
- tarea completada.

La actividad funcional es distinta del audit log técnico.

---

# 20. Notificaciones

Las notificaciones podrán generarse por eventos relevantes.

Canales:

- IN_APP — V1;
- EMAIL — V1;
- PUSH — arquitectura preparada, sin proveedor conectado en V1;
- SMS — arquitectura preparada, sin proveedor conectado en V1.

`ADR-NOTIF-001`: V1 implementa únicamente `IN_APP` y `EMAIL`. La notificación lógica se separa de su entrega por canal (`notification_deliveries`), lo que permite añadir `PUSH`/`SMS` en el futuro sin cambio estructural.

---

# 21. Exportaciones

Brika podrá generar exportaciones autorizadas, por ejemplo:

- dossier de operación;
- documentación;
- informes;
- información para cliente.

Las exportaciones sensibles deberán quedar auditadas.

---

# 22. Reporting

La V1 tendrá reporting básico sobre:

- operaciones;
- estados;
- actividad;
- documentación;
- tareas;
- producción;
- indicadores configurados.

Las capacidades avanzadas de BI podrán evolucionar posteriormente.

---

# 23. IA

Las funciones de IA estarán disponibles únicamente para usuarios y empresas autorizados.

Casos futuros o V1 según alcance definitivo:

- análisis documental;
- extracción de datos;
- asistencia al broker;
- resumen de operaciones;
- detección de documentación pendiente;
- generación de comunicaciones;
- apoyo al análisis.

Toda función de IA deberá respetar permisos y aislamiento de tenant.

---

# 24. Auditoría funcional

Las siguientes acciones requieren trazabilidad:

- login;
- cambios de permisos;
- cambios relevantes de cliente;
- cambios de operación;
- cambios de estado;
- subida de documentos;
- revisión documental;
- descarga de documentos;
- exportaciones;
- cambios de configuración;
- uso sensible de IA;
- integraciones.

---

# 25. Reglas funcionales transversales

1. Un usuario sólo podrá trabajar con recursos autorizados.
2. Un tenant nunca podrá acceder a recursos de otro tenant.
3. CLIENT sólo podrá acceder a información expresamente publicada.
4. Las acciones sensibles deberán quedar auditadas.
5. Los documentos no se sobrescribirán sin conservar historial.
6. Una simulación no equivale a una oferta bancaria.
7. Una oferta bancaria no equivale a financiación formalizada.
8. El scoring no equivale a una decisión bancaria.
9. Los procesos internos no serán visibles automáticamente al cliente.
10. Los cambios importantes deberán poder reconstruirse mediante historial.

---

# 26. Criterio de aceptación funcional

Una funcionalidad se considerará implementada cuando:

- cumpla el comportamiento descrito;
- respete roles y permisos;
- respete tenant isolation;
- respete la visibilidad del Portal Cliente;
- registre las acciones auditables;
- tenga pruebas;
- no rompa funcionalidades existentes.

---

# 27. Estado del documento

Este documento es la base funcional de Brika V1.

Los detalles técnicos deberán derivarse de estas reglas y no modificarlas silenciosamente.

Cualquier contradicción detectada entre documentos deberá resolverse mediante una decisión registrada en `DECISION_LOG.md`.

---

**FIN DEL FUNCTIONAL SPECIFICATION V1**
