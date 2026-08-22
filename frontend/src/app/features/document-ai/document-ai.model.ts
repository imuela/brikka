/** Sprint 33. Mirrors backend DocumentExtractionResponse
 * (com.brika.platform.ai.web.DocumentExtractionResponse). extractedData/confidence are typed
 * loosely (Object on the backend) since their shape depends on status:
 *  - NO_PROVIDER: extractedData is the raw (empty) fields array.
 *  - FAILED/COMPLETED: extractedData is { fields, summary, warnings, inconsistencies? }. */
export interface DocumentAiField {
  name: string;
  value: string;
  confidence?: number;
  page?: number | null;
}

export interface DocumentAiInconsistency {
  field: string;
  clientId: string;
  profileValue: number;
  documentValue: number;
}

export interface DocumentAiExtractedData {
  fields?: DocumentAiField[];
  summary?: string | null;
  warnings?: string[];
  inconsistencies?: DocumentAiInconsistency[];
}

export interface DocumentAiExtraction {
  id: string;
  documentVersionId: string;
  status: 'PENDING' | 'NO_PROVIDER' | 'FAILED' | 'COMPLETED';
  provider: string;
  model: string;
  extractedData: DocumentAiExtractedData | DocumentAiField[] | null;
  confidence: Record<string, unknown> | null;
  validatedBy: string | null;
  validatedAt: string | null;
  createdAt: string;
}
