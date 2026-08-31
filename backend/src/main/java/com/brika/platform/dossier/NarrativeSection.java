package com.brika.platform.dossier;

import java.util.List;

/**
 * BRIKKA V2 I5. One section of the deterministic case narrative. {@code key} is a stable machine id
 * ({@code situation}, {@code holders}, {@code property}, {@code financing}, {@code scoring}, {@code
 * viability}, {@code documentation}, {@code fees}); {@code title} is the Spanish heading; {@code
 * paragraphs} are ready-to-render Spanish sentences built only from stored data. Every section is
 * always present — when its data is missing it carries a single "no disponible"-style paragraph.
 */
public record NarrativeSection(String key, String title, List<String> paragraphs) {}
