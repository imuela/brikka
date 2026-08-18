# BRIKKA — Revisión de análisis de marca (Sprint 20)

Este documento acompaña a `BRIKKA_BRAND_GUIDELINES.md` y responde específicamente a: *qué se ha tomado de la referencia existente, qué es inferencia, y qué queda pendiente de aprobación humana antes de considerar la identidad visual definitiva.*

**Regla seguida:** generar este documento no aprueba la identidad. La identidad queda ANALIZADA → DOCUMENTADA → **PENDIENTE DE APROBACIÓN**.

---

## 1. Material de partida

Al iniciar el Sprint 20, `docs/branding/` contenía un único archivo:

- `file_0000000080d08243a1a6c92157dc0259.png` — 1024×1536px, PNG rasterizado. Póster de lanzamiento "BRIKKA ya está aquí." con logotipo, mockup de aplicación (laptop + móvil), y sección de beneficios/CTA.

No existían versiones contradictorias ni variantes previas — un único activo, tomado como referencia principal íntegra, sin necesidad de decidir entre propuestas.

**Limitación de origen:** es un archivo rasterizado (píxeles), no un archivo vectorial (SVG/AI/Figma) ni un archivo con capas. Esto significa que:
- Los colores se han podido extraer con precisión real (muestreo de píxeles vía `canvas.getImageData`, no estimación visual).
- El logotipo **no** se ha podido extraer como vector exacto (no hay trazados/paths disponibles en un PNG). Los archivos SVG del logotipo generados en `docs/branding/assets/` son una **interpretación propia** de la composición observada (símbolo "B" geométrico en dos tonos de azul + wordmark en mayúsculas), no una vectorización o trazado automático del archivo original.
- La tipografía exacta del wordmark no se ha podido identificar con certeza (sin metadatos de fuente en un PNG) — se ha documentado como "familia próxima a Poppins/Montserrat/Inter Bold/ExtraBold", una inferencia visual, no una identificación certera.

---

## 2. Qué se ha extraído directamente de la referencia (alta confianza)

| Elemento | Método | Resultado |
|---|---|---|
| Nombre de marca | Lectura directa del texto de la imagen | `BRIKKA` |
| Tagline | Lectura directa | `MORTGAGE OS` |
| Frase de posicionamiento | Lectura directa | "Tu negocio hipotecario. Una nueva forma de hacerlo." |
| Dominio mostrado | Lectura directa | `brikka.app` |
| Color de fondo oscuro | Muestreo de píxeles (canvas, ~170.000 muestras) | Navy, rango dominante `#000F20`–`#001028` |
| Color de acento azul | Muestreo de píxeles filtrado por tono | `#328CFA` (rgb 50,140,250), muestra dominante con 937 ocurrencias sobre el total muestreado |
| Blanco de texto/UI | Muestreo de píxeles | `#FFFFFF` |
| Gris claro de superficie (mockup app) | Muestreo de píxeles | `#F8F8F8` |
| Composición general | Inspección visual directa | Símbolo a la izquierda + wordmark mayúsculas a la derecha, tagline debajo en menor tamaño y color de acento |

---

## 3. Qué es inferencia o decisión de diseño (confianza media/baja — requiere aprobación explícita)

| Elemento | Qué se decidió | Por qué es inferencia |
|---|---|---|
| Trazado vectorial exacto del símbolo "B" | Interpretación geométrica propia (3 rectángulos redondeados superpuestos en degradado) | El original es un píxel raster; no existe trazado vectorial que extraer. La forma general (dos lóbulos + astil) es fiel a lo observado, pero no es una reproducción exacta trazo a trazo. |
| Familia tipográfica del wordmark | "Próxima a Poppins/Montserrat/Inter Bold/ExtraBold" | Estimación visual sobre una imagen rasterizada; no verificable sin el archivo fuente original o una herramienta de identificación de fuentes. Los SVG generados usan `Arial/Helvetica` como sustituto funcional, explícitamente marcado como no definitivo. |
| Verde de estado (`#4B9664` aprox.) | Extraído, pero de una muestra muy pequeña (10 píxeles en el muestreo) | La leyenda del gráfico de donut ocupa una zona minúscula de la imagen de 1024×1536; la muestra es estadísticamente débil. Verificar contra el archivo original a mayor resolución si está disponible. |
| Ámbar de estado (`#E6821E` aprox.) | Igual que el verde — muestra pequeña (13 píxeles) | Misma limitación. |
| Rojo de estado | **No extraído** — propuesto `#DC2626` (rojo semántico estándar, Tailwind red-600) | No se localizó una muestra de rojo fiable y suficientemente grande en la imagen de referencia. Es una propuesta razonada, no una extracción. |
| Paleta Material de la aplicación (`#328CFA` como `primary`) | **No aplicado** en este sprint | Cambiar el tema Material global es un rediseño visual de mayor alcance que "sustituir la marca visible"; se documenta como propuesta pendiente, no se ejecuta. |
| Tipografía de la aplicación (Roboto → alternativa de marca) | **No aplicado** en este sprint | Mismo razonamiento — cambiar la tipografía de toda la interfaz es un cambio de UI global no solicitado explícitamente para este sprint. |

---

## 4. Qué queda pendiente de aprobación

Antes de considerar esta identidad visual definitiva, requiere aprobación humana explícita de:

1. La paleta de colores completa (en particular verde/ámbar/rojo de estado, de confianza baja/nula en este análisis).
2. El trazado del símbolo "B" (interpretación propia, no extracción).
3. La familia tipográfica final del wordmark (no identificada con certeza).
4. Si se debe adoptar `#328CFA` como color primario del tema Angular Material de toda la aplicación, o mantener la paleta `azure` actual.
5. Si se debe sustituir Roboto por otra tipografía en la interfaz funcional (no solo en el wordmark de marca).
6. El uso del tagline "MORTGAGE OS" dentro de la propia aplicación (no aplicado en este sprint).

Hasta que estos puntos se aprueben explícitamente, la aplicación usa: el nombre `Brikka` en los lugares donde antes decía `Brika` (cambio de texto, bajo riesgo, ejecutado en este sprint) y el nuevo favicon SVG/PNG generado a partir del símbolo (cambio de icono, bajo riesgo, ejecutado en este sprint) — pero **no** se ha retemado el color ni la tipografía globales de la interfaz.

---

## 5. Limitación técnica de generación de activos

El entorno de ejecución de este sprint no dispone de ninguna herramienta de rasterización/edición de imágenes (`rsvg-convert`, `inkscape`, `cairosvg`, `imagemagick`/`convert` — todas ausentes). Para producir un PNG real a partir de los SVG generados se usó el navegador disponible en el entorno (`canvas.drawImage` + `canvas.toDataURL('image/png')`), lo cual permitió generar:

- `docs/branding/assets/brikka-favicon.png` (512×512, PNG real verificado por cabecera de archivo)
- El icono de 32×32 usado como `frontend/public/favicon.ico` (contenido PNG real; no es un contenedor `.ico` multi-resolución "correcto", ya que no hay codificador ICO disponible — la mayoría de navegadores y sistemas operativos modernos leen el contenido por *sniffing* y lo muestran correctamente, y además `frontend/public/favicon.svg` se sirve como icono primario vía `<link rel="icon" type="image/svg+xml">`, con el `.ico` solo como *fallback*).

No se generaron variantes JPG (no se identificó ningún uso que lo requiriera dentro del alcance de este sprint) ni tamaños adicionales de PNG más allá de 512×512 y 32×32.

---

## 6. Conclusión

La identidad **BRIKKA** queda: **ANALIZADA** (a partir del único activo disponible) → **DOCUMENTADA** (`BRIKKA_BRAND_GUIDELINES.md` + este documento) → **PENDIENTE DE APROBACIÓN** (colores de estado de baja confianza, trazado exacto del símbolo, tipografía definitiva, y alcance de aplicación al tema visual global de la interfaz). El nombre visible del producto y el favicon ya se han actualizado en la aplicación por ser cambios de bajo riesgo y alineados de forma inequívoca con la referencia; el resto de la identidad visual (color/tipografía a nivel de tema global) no se ha aplicado y espera revisión humana.
