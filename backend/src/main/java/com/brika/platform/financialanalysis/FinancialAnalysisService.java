package com.brika.platform.financialanalysis;

import com.brika.platform.bankrequest.BankOffer;
import com.brika.platform.bankrequest.BankOfferRepository;
import com.brika.platform.bankrequest.FinalFinancing;
import com.brika.platform.bankrequest.FinalFinancingRepository;
import com.brika.platform.casemgmt.CaseClient;
import com.brika.platform.casemgmt.CaseClientRepository;
import com.brika.platform.common.error.ValidationException;
import com.brika.platform.crm.ClientFinancialProfile;
import com.brika.platform.crm.ClientFinancialProfileRepository;
import com.brika.platform.financing.Simulation;
import com.brika.platform.financing.SimulationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 31. Runs a financial viability analysis for every client linked to a case. Quota source
 * priority (documented decision, see V23 migration comment and this sprint's report): (1) the
 * case's selected FinalFinancing -> BankOffer, the most authoritative once it exists; (2) else the
 * most recently created Simulation for the case; (3) else a structured FINANCING_DATA_REQUIRED
 * error. FinancingRequest is deliberately never used as a quota source by itself — it has no
 * interest rate column, so it cannot drive a computed payment alone (confirmed by inspecting its
 * schema before writing this class). The monthly payment is always recomputed via {@link
 * MortgagePaymentCalculator} from the source's own principal/rate/term — the source's own stored
 * payment/estimatedPayment field (a broker/bank-declared value, never a computed one) is not
 * trusted directly, since this sprint's job is to provide the first real, reproducible calculation.
 *
 * <p>Multi-client: no income-aggregation rule is documented anywhere (Legacy only aggregates at the
 * score level, never raw income) — every client linked to the case is analyzed individually,
 * sharing the same principal/rate/term/payment (one mortgage) but each with its own income/debts/
 * DTI/category. If any linked client lacks a usable financial profile, the whole run fails fast
 * with a structured error naming that client, rather than silently producing a partial result set.
 */
@Service
public class FinancialAnalysisService {

  private final CaseClientRepository caseClientRepository;
  private final ClientFinancialProfileRepository financialProfileRepository;
  private final FinalFinancingRepository finalFinancingRepository;
  private final BankOfferRepository bankOfferRepository;
  private final SimulationRepository simulationRepository;
  private final FinancialAnalysisResultRepository resultRepository;
  private final ViabilityClassifier viabilityClassifier;
  private final ObjectMapper objectMapper;

  public FinancialAnalysisService(
      CaseClientRepository caseClientRepository,
      ClientFinancialProfileRepository financialProfileRepository,
      FinalFinancingRepository finalFinancingRepository,
      BankOfferRepository bankOfferRepository,
      SimulationRepository simulationRepository,
      FinancialAnalysisResultRepository resultRepository,
      ViabilityClassifier viabilityClassifier,
      ObjectMapper objectMapper) {
    this.caseClientRepository = caseClientRepository;
    this.financialProfileRepository = financialProfileRepository;
    this.finalFinancingRepository = finalFinancingRepository;
    this.bankOfferRepository = bankOfferRepository;
    this.simulationRepository = simulationRepository;
    this.resultRepository = resultRepository;
    this.viabilityClassifier = viabilityClassifier;
    this.objectMapper = objectMapper;
  }

  public List<FinancialAnalysisResult> results(UUID caseId) {
    return resultRepository.findAllByCaseId(caseId);
  }

  @Transactional
  public List<FinancialAnalysisResult> run(UUID companyId, UUID caseId, UUID actorUserId) {
    List<CaseClient> caseClients = caseClientRepository.findAllByCaseId(caseId);
    if (caseClients.isEmpty()) {
      throw new ValidationException(
          "NO_CLIENTS_ON_CASE", "The case has no linked clients to analyze.");
    }

    QuotaSource quotaSource = resolveQuotaSource(caseId);
    BigDecimal monthlyPayment =
        MortgagePaymentCalculator.computeMonthlyPayment(
            quotaSource.principal(), quotaSource.interestRate(), quotaSource.termMonths());

    // Fail fast: validate every client has a usable profile before persisting anything, so a run
    // never produces a partial result set for the case.
    List<ClientFinancialProfile> profiles = new ArrayList<>();
    for (CaseClient caseClient : caseClients) {
      profiles.add(requireUsableFinancialProfile(caseClient.clientId()));
    }

    List<FinancialAnalysisResult> results = new ArrayList<>();
    for (ClientFinancialProfile profile : profiles) {
      BigDecimal existingMonthlyDebts = totalExistingMonthlyDebts(profile);
      BigDecimal dti =
          viabilityClassifier.computeDti(
              profile.monthlyIncome(), existingMonthlyDebts, monthlyPayment);
      String category = viabilityClassifier.classify(dti);
      String explanation =
          buildExplanation(
              profile, quotaSource, monthlyPayment, existingMonthlyDebts, dti, category);

      UUID resultId =
          resultRepository.insert(
              companyId,
              caseId,
              profile.clientId(),
              quotaSource.principal(),
              quotaSource.interestRate(),
              quotaSource.termMonths(),
              monthlyPayment,
              profile.monthlyIncome(),
              existingMonthlyDebts,
              dti,
              category,
              quotaSource.sourceType(),
              quotaSource.sourceId(),
              ViabilityClassifier.RULES_VERSION,
              explanation,
              actorUserId);
      results.add(resultRepository.findById(resultId).orElseThrow());
    }
    return results;
  }

  private ClientFinancialProfile requireUsableFinancialProfile(UUID clientId) {
    ClientFinancialProfile profile =
        financialProfileRepository
            .findByClientId(clientId)
            .orElseThrow(
                () ->
                    new ValidationException(
                        "FINANCIAL_PROFILE_REQUIRED",
                        "Client " + clientId + " has no financial profile yet."));
    if (profile.monthlyIncome() == null) {
      throw new ValidationException(
          "MONTHLY_INCOME_REQUIRED",
          "Client " + clientId + " has no monthlyIncome in their financial profile.");
    }
    if (profile.monthlyIncome().signum() <= 0) {
      throw new ValidationException(
          "MONTHLY_INCOME_INVALID", "Client " + clientId + " has a non-positive monthlyIncome.");
    }
    return profile;
  }

  private BigDecimal totalExistingMonthlyDebts(ClientFinancialProfile profile) {
    BigDecimal otherDebts =
        profile.otherDebtsMonthlyPayment() == null
            ? BigDecimal.ZERO
            : profile.otherDebtsMonthlyPayment();
    // creditCardDebt is a balance, not a monthly payment (see Sprint 30 field inventory) — only
    // otherDebtsMonthlyPayment feeds the DTI numerator, deliberately, not creditCardDebt.
    return otherDebts;
  }

  private QuotaSource resolveQuotaSource(UUID caseId) {
    Optional<FinalFinancing> finalFinancing = finalFinancingRepository.findByCaseId(caseId);
    if (finalFinancing.isPresent()) {
      Optional<BankOffer> offer = bankOfferRepository.findById(finalFinancing.get().bankOfferId());
      if (offer.isPresent()) {
        BankOffer bankOffer = offer.get();
        return new QuotaSource(
            bankOffer.amount(),
            bankOffer.interestRate(),
            bankOffer.termMonths(),
            "BANK_OFFER",
            bankOffer.id());
      }
    }

    List<Simulation> simulations = simulationRepository.findAllByCaseId(caseId);
    if (!simulations.isEmpty()) {
      Simulation simulation = simulations.get(0); // most recent first (SimulationRepository)
      return new QuotaSource(
          simulation.principal(),
          simulation.interestRate(),
          simulation.termMonths(),
          "SIMULATION",
          simulation.id());
    }

    throw new ValidationException(
        "FINANCING_DATA_REQUIRED",
        "The case needs a selected bank offer or a simulation (principal, interest rate and term)"
            + " before a financial analysis can be run.");
  }

  private String buildExplanation(
      ClientFinancialProfile profile,
      QuotaSource quotaSource,
      BigDecimal monthlyPayment,
      BigDecimal existingMonthlyDebts,
      BigDecimal dti,
      String category) {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("clientId", profile.clientId());
    root.put("monthlyIncome", profile.monthlyIncome());
    root.put("existingMonthlyDebts", existingMonthlyDebts);
    root.put("principal", quotaSource.principal());
    root.put("interestRate", quotaSource.interestRate());
    root.put("termMonths", quotaSource.termMonths());
    root.put("monthlyPayment", monthlyPayment);
    root.put("quotaSource", quotaSource.sourceType());
    root.put("quotaSourceId", quotaSource.sourceId());
    root.put("dtiPercent", dti);
    root.put("category", category);
    root.put("favorableMaxPercent", viabilityClassifier.favorableMaxPercent());
    root.put("revisarMaxPercent", viabilityClassifier.revisarMaxPercent());
    root.put("rulesVersion", ViabilityClassifier.RULES_VERSION);
    root.put("disclaimer", ViabilityClassifier.DISCLAIMER);
    try {
      return objectMapper.writeValueAsString(root);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize financial analysis explanation", e);
    }
  }

  private record QuotaSource(
      BigDecimal principal,
      BigDecimal interestRate,
      int termMonths,
      String sourceType,
      UUID sourceId) {}
}
