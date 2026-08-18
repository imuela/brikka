# BRIKKA — Guía de imagen de marca

**Estado:** ANALIZADA → DOCUMENTADA → **PENDIENTE DE APROBACIÓN**

Este documento no constituye una identidad de marca aprobada. Es la propuesta de referencia construida a partir del material existente en `docs/branding/` durante el Sprint 20, para revisión humana. Ver `BRIKKA_BRAND_REVIEW.md` para el desglose de qué es extracción directa de la referencia y qué es inferencia/decisión de diseño.

Fuente de referencia: `docs/branding/file_0000000080d08243a1a6c92157dc0259.png` (póster de lanzamiento "BRIKKA — MORTGAGE OS", 1024×1536, único activo de marca disponible en el repositorio al iniciar este sprint).

---

## 1. Identidad

**Nombre oficial del producto:** BRIKKA

**Uso recomendado del nombre:**
- En titulares, logotipo y primera mención por página: `BRIKKA` (mayúsculas, como aparece en la referencia) o `Brikka` (caja de título) según el contexto tipográfico — nunca `brikka` en minúsculas como palabra suelta en prosa.
- En texto corrido (párrafos, descripciones): `Brikka` es aceptable y más legible que `BRIKKA` en bloques largos.
- Subtítulo/posicionamiento visto en la referencia: **"MORTGAGE OS"** — se mantiene como posible tagline técnico, pendiente de aprobación de uso (no se ha aplicado en la aplicación en este sprint).
- Frase de cierre vista en la referencia: *"Tu negocio hipotecario. Una nueva forma de hacerlo."*
- Dominio observado en la referencia: `brikka.app` (no verificado como activo; no se ha tomado ninguna acción sobre dominios en este sprint, fuera de alcance).

**Forma correcta:**
- `Brikka`, `BRIKKA`

**Forma incorrecta:**
- `Brika`, `BRIKA`, `brikka` (minúscula suelta en un titular), `Brik-ka`, `BrikkA`

**Nombre de producto vs. referencias técnicas internas:**
El nombre de producto visible (`Brikka`) es independiente de los identificadores técnicos internos del repositorio, que permanecen sin cambios salvo razón técnica explícita (ver `12_DECISION_LOG.md`, ADR del Sprint 20, sección de excepciones documentadas): nombre de paquete Maven/npm, nombres de paquetes Java (`com.brika.platform.*`), nombre de base de datos, nombres de esquema/migraciones Flyway, client IDs de Keycloak (`brika-frontend`, `brika-portal-frontend`), nombres de realm (`brika`, `brika-portal`), y comentarios de código que documentan el contrato técnico de la API. Ninguno de estos es visible para el usuario final.

---

## 2. Logotipo

Activos disponibles en `docs/branding/assets/` (todos SVG, escalables sin pérdida; ver `BRIKKA_BRAND_REVIEW.md` para el origen y las limitaciones de generación):

| Archivo | Uso |
|---|---|
| `brikka-logo-primary.svg` | Logotipo principal — símbolo a color + wordmark en azul marino, para fondos claros. Uso por defecto en aplicación, documentación y comunicaciones. |
| `brikka-logo-dark.svg` | Símbolo a color + wordmark en blanco, para fondos oscuros (réplica del tratamiento visto en el póster de referencia). |
| `brikka-logo-light.svg` | Versión completamente blanca (símbolo y wordmark), para colocar sobre fondos de color de marca o fotográficos donde el logotipo a color no tenga suficiente contraste. |
| `brikka-logo-monochrome.svg` | Un único color de tinta (azul marino), para reproducción restringida: sellos, fax, impresión a una tinta, marcas de agua. |
| `brikka-logo-vertical.svg` | Símbolo apilado sobre el wordmark — para formatos cuadrados o muy estrechos (iconos de app, avatares). |
| `brikka-symbol.svg` | Solo el símbolo/isotipo ("B"), sin wordmark — para espacios reducidos donde el nombre completo no cabe (favicon, avatar, marca de agua pequeña). |
| `brikka-favicon.svg` / `brikka-favicon.png` (512×512) | Icono de pestaña del navegador. Ya integrado en la aplicación (`frontend/public/favicon.svg`, con `favicon.ico` como *fallback* rasterizado a 32×32 para navegadores sin soporte de favicon SVG). |

No existe, en este sprint, una versión bitmap multi-resolución `.ico` "correcta" (contenedor ICO con varias resoluciones incrustadas) porque no hay ninguna herramienta de rasterización de imágenes disponible en el entorno de ejecución — ver la limitación documentada en `BRIKKA_BRAND_REVIEW.md`.

---

## 3. Colores

Extraídos por muestreo de píxeles reales de la imagen de referencia (canvas, no estimación visual) salvo donde se indica lo contrario.

| Nombre | HEX | RGB | Función | Origen |
|---|---|---|---|---|
| Brikka Blue (primario) | `#328CFA` | `rgb(50,140,250)` | Color de marca principal — símbolo, acentos, enlaces destacados, CTA | Extraído (muestra dominante de la zona de acento azul brillante) |
| Brikka Navy (fondo oscuro / texto) | `#0B1930` | `rgb(11,25,48)` | Fondo oscuro de comunicaciones de marca; color de texto del wordmark sobre fondo claro | Extraído (aproximación al navy dominante del fondo del póster, rango real `#000F20`–`#001028`) |
| Blanco | `#FFFFFF` | `rgb(255,255,255)` | Texto sobre fondo oscuro, fondo de tarjetas de producto en el mockup | Extraído |
| Gris claro (fondo de superficie) | `#F8F8F8` | `rgb(248,248,248)` | Fondo de paneles/tarjetas de la interfaz mostrada en el mockup | Extraído |
| Verde de estado (positivo) | `#4B9664` (aprox.) | `rgb(75,150,100)` | Estados positivos/activos en gráficos (ej. "Nuevas") | Extraído con baja confianza — la zona muestreada (leyenda del donut chart) es muy pequeña en la imagen fuente; **verificar contra la referencia original antes de usar en producción** |
| Ámbar de estado (aviso) | `#E6821E` (aprox.) | `rgb(230,130,30)` | Estados intermedios ("En estudio", "En tasación") | Extraído con baja confianza, misma limitación que el verde |
| Rojo de estado (negativo) | `#DC2626` (propuesto) | `rgb(220,38,38)` | Estados de error/cancelación | **No extraído** — no se identificó una muestra de rojo suficientemente grande/fiable en la referencia. Es un rojo semántico estándar propuesto, pendiente de aprobación explícita. |

**Colores ya en uso en la aplicación (no modificados en este sprint):** el tema Angular Material actual usa `mat.$azure-palette` como color primario (`frontend/src/styles.scss`), una paleta azul ya razonablemente próxima al Brikka Blue extraído. Adoptar `#328CFA` como paleta Material primaria exacta (retema completo de botones, enlaces, estados de foco, etc.) es un cambio visual con superficie mucho mayor que el alcance de este sprint ("sustituir la marca visible", no "rediseñar el tema") y queda **pendiente de aprobación como trabajo de seguimiento**, no ejecutado aquí.

---

## 4. Tipografía

**Wordmark de marca (logotipo):** sans-serif geométrica de trazo grueso, todo mayúsculas, sin serifas — visualmente próxima a familias como *Poppins*, *Montserrat* o *Inter* en su peso Bold/ExtraBold/Black. No ha sido posible identificar con certeza la familia tipográfica exacta a partir de una imagen rasterizada (sin metadatos de fuente incrustados); los SVG de logotipo generados en este sprint usan una pila `Arial, Helvetica, sans-serif` en `font-weight: 800` como aproximación funcional, **no como recomendación tipográfica final**.

**Tipografía de la aplicación (sin cambios en este sprint):** Roboto (`frontend/src/index.html`, Google Fonts; `frontend/src/styles.scss`), la tipografía por defecto de Angular Material. Roboto es geométricamente compatible como tipografía de interfaz (legible en tamaños pequeños, buen soporte de pesos) aunque no es la misma familia que el wordmark de marca — patrón habitual en identidades SaaS (wordmark distintivo + tipografía de sistema para la UI funcional). Adoptar una tipografía de marca distinta para toda la interfaz es, igual que el color, un cambio de mayor alcance que el de este sprint y queda pendiente de aprobación.

**Jerarquía recomendada (para el documento de marca, no aplicada al tema global de la app):**
- Títulos de marca / hero: Bold/ExtraBold, mayúsculas o versalitas, tracking ligeramente abierto (como en la referencia: "BRIKKA ya está aquí.")
- Títulos de sección (H1/H2 de la app): Roboto Medium/Bold, tal como ya se usa
- Cuerpo de texto: Roboto Regular
- Botones/navegación: Roboto Medium, como ya se usa en Angular Material

---

## 5. UI — referencia inicial

Referencia de estilo observada en el mockup de la propia imagen de marca (panel de aplicación mostrado en el póster), documentada para futuros sprints de diseño — **no aplicada retroactivamente a los componentes existentes en este sprint** (fuera de alcance: "no modificar comportamiento... salvo imprescindible").

- **Botones:** esquinas redondeadas suaves (no totalmente circulares), color primario Brikka Blue sólido para la acción principal, contorno/texto azul para acciones secundarias — coherente con el patrón `mat-flat-button` / `mat-button` ya usado en la aplicación.
- **Inputs/selects:** fondo blanco o gris muy claro, borde sutil, foco en Brikka Blue — coherente con `mat-form-field` (appearance `fill`) ya usado.
- **Cards:** fondo blanco, esquinas redondeadas, sombra suave — visible en las tarjetas de estadísticas del mockup ("Operaciones activas", "Documentos pendientes"...).
- **Tablas:** cabecera en gris claro/blanco, filas alternadas o separadas por línea fina — coherente con el patrón ya establecido en `case-list`, `client-list`, etc.
- **Badges/estados:** círculo o pastilla de color por estado (verde/ámbar/rojo/azul según la semántica ya definida en `status-labels.ts`) — el mockup usa puntos de color junto al texto del estado en el gráfico de donut ("Nuevas", "En estudio"...).
- **Navegación:** barra lateral con iconos + etiqueta, ítem activo resaltado — igual patrón que el `sidenav` ya implementado desde Sprint 13.
- **Estados vacíos:** mensaje honesto en español, sin iconografía inventada — coherente con el patrón ya establecido en Portal Cliente ("Sin notificaciones todavía.", Sprint 19) y Tareas/Comunicaciones (Sprint 17).
- **Modales/diálogos:** título + contenido + acciones alineadas a la derecha — patrón `mat-dialog` ya usado en toda la aplicación (Sprint 14 en adelante).
- **Mensajes de error:** texto en rojo semántico, en español, siguiendo `friendlyErrorMessage`/`core/http/error-messages.ts` (ya existente, sin cambios).

---

## 6. Uso de marca

- **Márgenes mínimos:** dejar alrededor del logotipo un margen libre no menor a la altura del símbolo/isotipo (regla estándar de "área de respeto" para logotipos con símbolo + wordmark).
- **Tamaño mínimo:** el wordmark completo no debe reproducirse por debajo de ~80px de ancho (legibilidad); el símbolo solo, no por debajo de ~16px (favicon).
- **Fondos permitidos:** blanco, gris muy claro, Brikka Navy oscuro, fotografías con suficiente contraste (usando la versión `light` en blanco plano).
- **Usos incorrectos:**
  - No estirar ni deformar el símbolo ni el wordmark (mantener proporción).
  - No rotar el símbolo.
  - No recolorear el símbolo fuera de las variantes documentadas (primary/dark/light/monochrome).
  - No colocar el wordmark en azul marino sobre fondo oscuro, ni en blanco sobre fondo claro (contraste insuficiente) — usar siempre la variante correspondiente al fondo.
  - No añadir efectos (sombras paramétricas, biseles, contornos) no presentes en las variantes documentadas.
- **Separación símbolo/wordmark:** la proporción usada en los SVG generados (símbolo ≈ altura total del wordmark, separación ≈ 1 carácter de ancho) sigue la composición horizontal vista en la referencia; mantener esa proporción al reescalar.

---

## 7. Historial

- **Sprint 20:** primera versión de este documento. Identidad analizada y documentada a partir de la única referencia visual existente (`docs/branding/file_0000000080d08243a1a6c92157dc0259.png`). Pendiente de aprobación humana antes de considerarse definitiva. Ver `12_DECISION_LOG.md` (ADR del Sprint 20) y `BRIKKA_BRAND_REVIEW.md`.
