package com.brika.platform.ai;

import java.net.URI;

/**
 * Sprint 33. What a real Worker needs to actually fetch the document's bytes for a REAL extraction
 * call: a short-lived presigned URL (never raw storage credentials — the Worker stays
 * network-isolated per ADR-AI-001, it never talks to MinIO/S3 directly with credentials) plus the
 * filename/mimeType the provider needs to know how to interpret the content.
 */
public record DocumentDownloadContext(URI downloadUrl, String filename, String mimeType) {}
