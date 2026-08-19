# BRIKA — PRODUCT SPECIFICATION V1

## 1. Producto

Brika es un SaaS multiempresa para brokers hipotecarios. Su propósito es centralizar la captación, análisis, documentación, búsqueda de financiación, relación con entidades, comunicación con clientes y seguimiento de operaciones.

## 2. Público objetivo

V1 está orientada a:
- brokers hipotecarios;
- empresas de intermediación hipotecaria;
- equipos con manager y varios brokers.

La arquitectura debe permitir una futura adaptación a inmobiliarias.

## 3. Propuesta de valor

Brika debe reducir:
- trabajo manual;
- duplicidad de información;
- dependencia de hojas de cálculo;
- pérdida de documentos;
- falta de trazabilidad;
- comunicaciones dispersas;
- análisis inconsistente.

Debe aumentar:
- control;
- trazabilidad;
- velocidad;
- calidad documental;
- capacidad de análisis;
- colaboración con el cliente;
- capacidad de seguimiento.

## 4. Núcleo del producto

El núcleo está compuesto por:
1. CRM de clientes.
2. Gestión de operaciones.
3. Gestión de inmuebles.
4. Motor documental.
5. Motor de análisis.
6. Motor bancario.
7. Scoring.
8. Workflow.
9. Portal Cliente.
10. Comunicaciones.
11. Auditoría.
12. IA.

## 5. Tipos de operación

Brika debe soportar el catálogo funcional de operaciones definido para V1. El catálogo será configurable y versionado, evitando codificar reglas de negocio directamente en la interfaz.

Los tipos concretos se almacenarán como datos de catálogo y podrán incorporar reglas específicas.

## 6. Principio de producto

La operación hipotecaria es el eje central. Cliente, inmueble, documentos, financiación, bancos, tareas, comunicaciones, scoring e IA se relacionan con la operación sin convertirse en sistemas aislados.

## 7. Portal Cliente

El Portal Cliente es un producto funcional propio dentro de Brika:
- acceso independiente;
- información publicada explícitamente;
- documentación;
- solicitudes;
- mensajería;
- notificaciones;
- actualización limitada de datos.

## 8. Evolución

El dominio debe poder evolucionar hacia nuevas verticales sin duplicar todo el núcleo.
