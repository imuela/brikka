package com.brika.platform.storage;

/**
 * 18_STORAGE_SPECIFICATION.md §3: "No usar nombres proporcionados por el usuario como autoridad de
 * seguridad."
 */
final class SafeFilenames {

  private SafeFilenames() {}

  static String sanitize(String originalFilename) {
    if (originalFilename == null || originalFilename.isBlank()) {
      return "file";
    }
    String base = originalFilename.replaceAll("[\\\\/]", "_");
    String safe = base.replaceAll("[^A-Za-z0-9._-]", "_");
    return safe.length() > 200 ? safe.substring(0, 200) : safe;
  }
}
