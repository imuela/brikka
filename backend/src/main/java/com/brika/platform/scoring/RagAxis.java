package com.brika.platform.scoring;

/**
 * BRIKKA V2 I2. One contributing signal of the case RAG indicator. {@code axis} is a stable machine
 * key ({@code scoring} | {@code viability} | {@code documentation}); {@code detail} is a short
 * human-readable Spanish explanation of why the axis has that level.
 */
public record RagAxis(String axis, RagLevel level, String detail) {}
