/** Mirrors backend MatchResult (ADR-BANKENGINE-001 §4/§8) — the only possible outcomes of
 * evaluating a rule or the global result. */
export const MATCH_RESULTS = ['PASS', 'FAIL', 'WARNING', 'NOT_EVALUATED', 'ERROR'] as const;
export type MatchResultValue = (typeof MATCH_RESULTS)[number];

/** Mirrors backend BankMatchRuleOverrideResponse. */
export interface BankMatchRuleOverride {
  id: string;
  previousResult: string;
  newResult: string;
  reason: string;
  overriddenBy: string;
  overriddenAt: string;
}

/** Mirrors backend RuleResultResponse. `expectedValue`/`evaluatedValue` are schemaless (mirror
 * whatever shape the rule's `value` had) — rendered defensively, never assumed. */
export interface BankMatchRuleResult {
  id: string;
  ruleId: string;
  field: string;
  operator: string;
  expectedValue: unknown;
  evaluatedValue: unknown;
  result: string;
  reason: string;
  effectiveResult: string;
  overrideCount: number;
  overrides: BankMatchRuleOverride[];
}

/** Mirrors backend BankMatchResultResponse. `inputSnapshot` is the schemaless server-built
 * snapshot (LTV, requestedAmount, termMonths) — rendered defensively. */
export interface BankMatchResult {
  id: string;
  caseId: string;
  bankId: string;
  bankCriteriaVersionId: string;
  globalResult: string;
  effectiveGlobalResult: string;
  evaluatedAt: string;
  inputSnapshot: unknown;
  ruleResults: BankMatchRuleResult[];
}

/** Mirrors backend CreateBankMatchRuleOverrideApiRequest. */
export interface CreateBankMatchRuleOverrideRequest {
  previousResult: string;
  newResult: string;
  reason: string;
}
