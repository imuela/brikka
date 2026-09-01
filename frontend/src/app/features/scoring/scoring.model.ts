/**
 * BRIKKA V2 I2. Mirrors backend CaseRagResponse
 * (com.brika.platform.scoring.web.CaseRagResponse) exactly — no fields or logic beyond what the
 * endpoint returns. The qualitative RAG indicator of a case combines the operation scoring, the
 * viability analysis and the document checklist; it introduces no new business variable.
 */
export type RagLevel = 'GREEN' | 'AMBER' | 'RED' | 'NOT_EVALUATED';

export interface CaseRagAxis {
  /** Stable machine key: 'scoring' | 'viability' | 'documentation'. */
  axis: string;
  level: RagLevel;
  /** Short Spanish explanation of why the axis has that level. */
  detail: string;
}

export interface CaseRag {
  rag: RagLevel;
  axes: CaseRagAxis[];
}
