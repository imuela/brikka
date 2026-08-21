/** Sprint 31. Mirrors backend FinancialAnalysisResultResponse exactly
 * (com.brika.platform.financialanalysis.web.FinancialAnalysisResultResponse). */
export interface FinancialAnalysisExplanation {
  clientId: string;
  monthlyIncome: number;
  existingMonthlyDebts: number;
  principal: number;
  interestRate: number;
  termMonths: number;
  monthlyPayment: number;
  quotaSource: string;
  quotaSourceId: string;
  dtiPercent: number;
  category: string;
  favorableMaxPercent: number;
  revisarMaxPercent: number;
  rulesVersion: string;
  disclaimer: string;
}

export interface FinancialAnalysisResult {
  id: string;
  caseId: string;
  clientId: string;
  principal: number;
  interestRate: number;
  termMonths: number;
  monthlyPayment: number;
  monthlyIncome: number;
  existingMonthlyDebts: number;
  dtiPercent: number;
  viabilityCategory: string;
  quotaSource: string;
  quotaSourceId: string;
  rulesVersion: string;
  explanation: FinancialAnalysisExplanation;
  calculatedBy: string;
  calculatedAt: string;
}
