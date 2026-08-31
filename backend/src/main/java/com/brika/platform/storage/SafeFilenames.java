package com.brika.platform.storage;

/**
 * 18_STORAGE_SPECIFICATION.md §3: "No usar nombres proporcionados por el usuario como autoridad de
 * seguridad." Public since BRIKKA V2 I5 so the case documents ZIP can sanitize every path segment
 * it derives from document metadata (type name, holder name, original filename) — a document name
 * can never steer a path inside the archive.
 */
public final class SafeFilenames {

  private SafeFilenames() {}

  public static String sanitize(String originalFilename) {
    if (originalFilename == null || originalFilename.isBlank()) {
      return "file";
    }
    String base = originalFilename.replaceAll("[\\\\/]", "_");
    String safe = base.replaceAll("[^A-Za-z0-9._-]", "_");
    return safe.length() > 200 ? safe.substring(0, 200) : safe;
  }
}
