package com.brika.platform.dossier;

import com.brika.platform.casefee.CaseFee;
import com.brika.platform.casefee.CaseFeeService;
import com.brika.platform.casemgmt.Case;
import com.brika.platform.casemgmt.CaseClient;
import com.brika.platform.casemgmt.CaseClientRepository;
import com.brika.platform.casemgmt.CaseStatus;
import com.brika.platform.casemgmt.ParticipationType;
import com.brika.platform.crm.Client;
import com.brika.platform.crm.ClientFinancialProfile;
import com.brika.platform.crm.ClientFinancialProfileRepository;
import com.brika.platform.crm.ClientRepository;
import com.brika.platform.document.CaseChecklist;
import com.brika.platform.document.CaseChecklistService;
import com.brika.platform.financialanalysis.FinancialAnalysisResult;
import com.brika.platform.financialanalysis.FinancialAnalysisResultRepository;
import com.brika.platform.financing.FinancingRequest;
import com.brika.platform.financing.FinancingRequestRepository;
import com.brika.platform.financing.Simulation;
import com.brika.platform.financing.SimulationComputation;
import com.brika.platform.financing.SimulationRepository;
import com.brika.platform.financing.SimulationService;
import com.brika.platform.property.Property;
import com.brika.platform.property.PropertyRepository;
import com.brika.platform.scoring.CaseRagIndicator;
import com.brika.platform.scoring.CaseRagService;
import com.brika.platform.scoring.RagLevel;
import com.brika.platform.scoring.ScoringResult;
import com.brika.platform.scoring.ScoringResultRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * BRIKKA V2 I5. Builds the {@link CaseNarrative} — a deterministic, structured summary of a case
 * from data already stored in Brikka. No AI, no {@code AiProvider}, no external call, no clock read
 * in the text: {@code narrate(x)} always returns the same result for the same stored data. It only
 * reports what is stored; missing data yields an explicit "no disponible"-style sentence, never an
 * invented value, a recommendation or a new financial conclusion.
 *
 * <p>Scoring and viability rows are additionally filtered by {@code case.companyId()} (defence in
 * depth, mirroring {@code CaseRagService}) so another tenant's data can never surface even if it
 * shared the {@code case_id}.
 */
@Service
public class CaseNarrativeService {

  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneOffset.UTC);

  private final CaseClientRepository caseClientRepository;
  private final ClientRepository clientRepository;
  private final ClientFinancialProfileRepository financialProfileRepository;
  private final PropertyRepository propertyRepository;
  private final FinancingRequestRepository financingRequestRepository;
  private final SimulationRepository simulationRepository;
  private final SimulationService simulationService;
  private final ScoringResultRepository scoringResultRepository;
  private final FinancialAnalysisResultRepository financialAnalysisRepository;
  private final CaseFeeService caseFeeService;
  private final CaseChecklistService caseChecklistService;
  private final CaseRagService caseRagService;

  public CaseNarrativeService(
      CaseClientRepository caseClientRepository,
      ClientRepository clientRepository,
      ClientFinancialProfileRepository financialProfileRepository,
      PropertyRepository propertyRepository,
      FinancingRequestRepository financingRequestRepository,
      SimulationRepository simulationRepository,
      SimulationService simulationService,
      ScoringResultRepository scoringResultRepository,
      FinancialAnalysisResultRepository financialAnalysisRepository,
      CaseFeeService caseFeeService,
      CaseChecklistService caseChecklistService,
      CaseRagService caseRagService) {
    this.caseClientRepository = caseClientRepository;
    this.clientRepository = clientRepository;
    this.financialProfileRepository = financialProfileRepository;
    this.propertyRepository = propertyRepository;
    this.financingRequestRepository = financingRequestRepository;
    this.simulationRepository = simulationRepository;
    this.simulationService = simulationService;
    this.scoringResultRepository = scoringResultRepository;
    this.financialAnalysisRepository = financialAnalysisRepository;
    this.caseFeeService = caseFeeService;
    this.caseChecklistService = caseChecklistService;
    this.caseRagService = caseRagService;
  }

  public CaseNarrative narrate(Case theCase) {
    List<CaseClient> holders =
        caseClientRepository.findAllByCaseId(theCase.id()).stream()
            .filter(CaseNarrativeService::isHolder)
            .toList();
    Map<UUID, Client> clientsById = new LinkedHashMap<>();
    for (CaseClient holder : holders) {
      clientRepository.findById(holder.clientId()).ifPresent(c -> clientsById.put(c.id(), c));
    }
    List<UUID> holderIds = holders.stream().map(CaseClient::clientId).toList();

    List<NarrativeSection> sections = new ArrayList<>();
    sections.add(situationSection(theCase));
    sections.add(holdersSection(holders, clientsById));
    sections.add(propertySection(theCase));
    sections.add(financingSection(theCase));
    sections.add(scoringSection(theCase, holderIds));
    sections.add(viabilitySection(theCase, clientsById));
    sections.add(documentationSection(theCase, holderIds));
    sections.add(feesSection(theCase));
    return new CaseNarrative(sections);
  }

  private NarrativeSection situationSection(Case theCase) {
    List<String> paragraphs = new ArrayList<>();
    paragraphs.add(
        "El expediente "
            + theCase.reference()
            + " se encuentra en estado «"
            + statusLabel(theCase.status())
            + "».");
    if (notBlank(theCase.operationType())) {
      paragraphs.add("Tipo de operación: " + theCase.operationType() + ".");
    }
    if (theCase.requestedAmount() != null) {
      paragraphs.add(
          "Importe solicitado en el expediente: " + money(theCase.requestedAmount()) + ".");
    }
    if (theCase.createdAt() != null) {
      paragraphs.add("Expediente creado el " + DATE.format(theCase.createdAt()) + " (UTC).");
    }
    return new NarrativeSection("situation", "Situación del expediente", paragraphs);
  }

  private NarrativeSection holdersSection(List<CaseClient> holders, Map<UUID, Client> clientsById) {
    List<String> paragraphs = new ArrayList<>();
    if (holders.isEmpty()) {
      paragraphs.add("El expediente no tiene titulares asociados.");
      return new NarrativeSection("holders", "Titulares", paragraphs);
    }
    List<String> names =
        holders.stream().map(h -> fullName(clientsById.get(h.clientId()), h.clientId())).toList();
    paragraphs.add(
        "El expediente tiene "
            + holders.size()
            + (holders.size() == 1 ? " titular: " : " titulares: ")
            + String.join(", ", names)
            + ".");
    for (CaseClient holder : holders) {
      String name = fullName(clientsById.get(holder.clientId()), holder.clientId());
      Optional<ClientFinancialProfile> profile =
          financialProfileRepository.findByClientId(holder.clientId());
      if (profile.isEmpty()) {
        paragraphs.add(name + ": sin perfil financiero registrado.");
        continue;
      }
      paragraphs.add(name + ": " + describeProfile(profile.get()));
    }
    return new NarrativeSection("holders", "Titulares", paragraphs);
  }

  private String describeProfile(ClientFinancialProfile profile) {
    List<String> parts = new ArrayList<>();
    if (profile.monthlyIncome() != null) {
      parts.add("ingresos mensuales " + money(profile.monthlyIncome()));
    }
    if (notBlank(profile.employmentType())) {
      String employment = profile.employmentType();
      if (notBlank(profile.employerName())) {
        employment += " en «" + profile.employerName() + "»";
      }
      if (profile.yearsEmployed() != null) {
        employment += " con " + profile.yearsEmployed() + " años de antigüedad";
      }
      parts.add(employment);
    }
    if (profile.savings() != null) {
      parts.add("ahorro " + money(profile.savings()));
    }
    if (profile.otherDebtsMonthlyPayment() != null) {
      parts.add("deudas mensuales " + money(profile.otherDebtsMonthlyPayment()));
    }
    return parts.isEmpty()
        ? "perfil financiero sin datos económicos."
        : String.join("; ", parts) + ".";
  }

  private NarrativeSection propertySection(Case theCase) {
    List<String> paragraphs = new ArrayList<>();
    Optional<Property> property = propertyRepository.findByCaseId(theCase.id());
    if (property.isEmpty()) {
      paragraphs.add("Sin inmueble registrado.");
      return new NarrativeSection("property", "Operación e inmueble", paragraphs);
    }
    Property p = property.get();
    List<String> parts = new ArrayList<>();
    if (notBlank(p.propertyType())) {
      parts.add("tipo " + p.propertyType());
    }
    if (p.valuation() != null) {
      parts.add("valoración " + money(p.valuation()));
    }
    if (p.purchasePrice() != null) {
      parts.add("precio de compra " + money(p.purchasePrice()));
    }
    paragraphs.add(
        parts.isEmpty()
            ? "Inmueble registrado sin datos económicos."
            : "Inmueble: " + String.join(", ", parts) + ".");
    BigDecimal ltv = approximateLtv(theCase, p);
    if (ltv != null) {
      paragraphs.add("LTV aproximado sobre el importe solicitado: " + rate(ltv) + " %.");
    }
    return new NarrativeSection("property", "Operación e inmueble", paragraphs);
  }

  /** Same shape as ScoreInputSnapshotFactory: amount / MIN(valuation, purchasePrice), scale 2. */
  private BigDecimal approximateLtv(Case theCase, Property property) {
    BigDecimal amount =
        financingRequestRepository.findAllByCaseId(theCase.id()).stream()
            .findFirst()
            .map(FinancingRequest::requestedAmount)
            .orElse(theCase.requestedAmount());
    if (amount == null || amount.signum() <= 0) {
      return null;
    }
    BigDecimal denominator = null;
    if (property.valuation() != null && property.purchasePrice() != null) {
      denominator = property.valuation().min(property.purchasePrice());
    } else if (property.valuation() != null) {
      denominator = property.valuation();
    } else if (property.purchasePrice() != null) {
      denominator = property.purchasePrice();
    }
    if (denominator == null || denominator.signum() == 0) {
      return null;
    }
    return amount.multiply(BigDecimal.valueOf(100)).divide(denominator, 2, RoundingMode.HALF_UP);
  }

  private NarrativeSection financingSection(Case theCase) {
    List<String> paragraphs = new ArrayList<>();
    List<FinancingRequest> requests = financingRequestRepository.findAllByCaseId(theCase.id());
    List<Simulation> simulations = simulationRepository.findAllByCaseId(theCase.id());

    if (requests.isEmpty() && simulations.isEmpty()) {
      paragraphs.add("Sin simulaciones ni solicitudes de financiación registradas.");
      return new NarrativeSection("financing", "Financiación", paragraphs);
    }
    for (FinancingRequest request : requests) {
      paragraphs.add(
          "Solicitud de financiación: "
              + money(request.requestedAmount())
              + " a "
              + request.termMonths()
              + " meses, estado "
              + request.status()
              + ".");
    }
    if (!simulations.isEmpty()) {
      paragraphs.add(
          "Se han registrado "
              + simulations.size()
              + (simulations.size() == 1 ? " simulación." : " simulaciones."));
      // findAllByCaseId is ordered created_at DESC -> the first is the most recent.
      paragraphs.add(describeSimulation(simulations.get(0)));
    }
    return new NarrativeSection("financing", "Financiación", paragraphs);
  }

  private String describeSimulation(Simulation simulation) {
    StringBuilder sb =
        new StringBuilder("Simulación más reciente (")
            .append(DATE.format(simulation.createdAt()))
            .append(" UTC): tipo de interés ")
            .append(interestTypeLabel(simulation.interestType()))
            .append(", importe ")
            .append(money(simulation.principal()))
            .append(" a ")
            .append(simulation.termMonths())
            .append(" meses, tipo final ")
            .append(rate(simulation.finalInterestRate()))
            .append(" %, cuota estimada ")
            .append(money(simulation.estimatedPayment()));
    if ("MIXED".equals(simulation.interestType()) && simulation.fixedPeriodMonths() != null) {
      SimulationComputation computation = simulationService.computationOf(simulation);
      sb.append(" durante el tramo fijo de ")
          .append(simulation.fixedPeriodMonths())
          .append(" meses");
      if (computation.variablePhaseEstimatedPayment() != null
          && computation.variablePhaseFinalRate() != null) {
        sb.append("; después, cuota estimada ")
            .append(money(computation.variablePhaseEstimatedPayment()))
            .append(" al ")
            .append(rate(computation.variablePhaseFinalRate()))
            .append(" %");
      }
    }
    return sb.append(".").toString();
  }

  private NarrativeSection scoringSection(Case theCase, List<UUID> holderIds) {
    List<String> paragraphs = new ArrayList<>();
    Optional<ScoringResult> latest =
        scoringResultRepository.findAllByCaseId(theCase.id()).stream()
            .filter(r -> r.companyId().equals(theCase.companyId()))
            .findFirst();
    if (latest.isEmpty()) {
      paragraphs.add("Scoring de la operación no calculado.");
    } else {
      ScoringResult result = latest.get();
      paragraphs.add(
          "Scoring de la operación: categoría "
              + result.category()
              + " (puntuación "
              + result.totalScore().stripTrailingZeros().toPlainString()
              + ").");
    }

    CaseRagIndicator rag =
        caseRagService.evaluate(
            theCase.companyId(), theCase.id(), theCase.operationType(), holderIds);
    if (rag.level() == RagLevel.NOT_EVALUATED) {
      paragraphs.add("Indicador RAG del expediente: sin evaluar.");
    } else {
      String axes =
          rag.axes().stream()
              .map(a -> ragAxisLabel(a.axis()) + ": " + ragLabel(a.level()))
              .reduce((a, b) -> a + ", " + b)
              .orElse("");
      paragraphs.add("Indicador RAG del expediente: " + ragLabel(rag.level()) + " (" + axes + ").");
    }
    return new NarrativeSection("scoring", "Scoring e indicador RAG", paragraphs);
  }

  private NarrativeSection viabilitySection(Case theCase, Map<UUID, Client> clientsById) {
    List<String> paragraphs = new ArrayList<>();
    Map<UUID, FinancialAnalysisResult> latestByClient = new LinkedHashMap<>();
    for (FinancialAnalysisResult result :
        financialAnalysisRepository.findAllByCaseId(theCase.id())) {
      if (!result.companyId().equals(theCase.companyId())) {
        continue;
      }
      latestByClient.putIfAbsent(result.clientId(), result);
    }
    if (latestByClient.isEmpty()) {
      paragraphs.add("Sin análisis de viabilidad ejecutado.");
      return new NarrativeSection("viability", "Viabilidad y DTI", paragraphs);
    }
    for (FinancialAnalysisResult result : latestByClient.values()) {
      paragraphs.add(
          fullName(clientsById.get(result.clientId()), result.clientId())
              + ": viabilidad "
              + result.viabilityCategory()
              + " (DTI "
              + rate(result.dtiPercent())
              + " %).");
    }
    return new NarrativeSection("viability", "Viabilidad y DTI", paragraphs);
  }

  private NarrativeSection documentationSection(Case theCase, List<UUID> holderIds) {
    List<String> paragraphs = new ArrayList<>();
    CaseChecklist checklist =
        caseChecklistService.checklist(theCase.id(), theCase.operationType(), holderIds);
    if (checklist.mandatoryTotal() == 0) {
      paragraphs.add("Sin requisitos documentales para este tipo de operación.");
      return new NarrativeSection("documentation", "Documentación", paragraphs);
    }
    int approved = checklist.mandatoryTotal() - checklist.mandatoryMissing();
    paragraphs.add(
        "Documentación obligatoria: "
            + approved
            + " de "
            + checklist.mandatoryTotal()
            + " aprobados.");
    if (checklist.mandatoryMissing() > 0) {
      paragraphs.add(
          "Faltan " + checklist.mandatoryMissing() + " documentos obligatorios por aprobar.");
    } else {
      paragraphs.add("Toda la documentación obligatoria está aprobada.");
    }
    if (checklist.optionalTotal() > 0) {
      paragraphs.add(
          "Documentación opcional: "
              + (checklist.optionalTotal() - checklist.optionalMissing())
              + " de "
              + checklist.optionalTotal()
              + " aprobados.");
    }
    return new NarrativeSection("documentation", "Documentación", paragraphs);
  }

  private NarrativeSection feesSection(Case theCase) {
    List<String> paragraphs = new ArrayList<>();
    Optional<CaseFee> fee = caseFeeService.find(theCase.id());
    if (fee.isEmpty()) {
      paragraphs.add("Sin honorarios configurados.");
    } else {
      CaseFee f = fee.get();
      paragraphs.add(
          "Honorarios: "
              + money(f.calculatedAmount())
              + " ("
              + f.feeType()
              + ", estado "
              + f.status()
              + ").");
    }
    return new NarrativeSection("fees", "Honorarios", paragraphs);
  }

  private static boolean isHolder(CaseClient caseClient) {
    return caseClient.participationType() == ParticipationType.HOLDER
        || caseClient.participationType() == ParticipationType.CO_HOLDER;
  }

  private static String fullName(Client client, UUID fallbackId) {
    if (client == null) {
      return fallbackId.toString();
    }
    String name =
        ((client.firstName() == null ? "" : client.firstName())
                + " "
                + (client.lastName() == null ? "" : client.lastName()))
            .trim();
    return name.isEmpty() ? fallbackId.toString() : name;
  }

  private static boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }

  private static String money(BigDecimal value) {
    return value == null ? "no disponible" : value.stripTrailingZeros().toPlainString() + " €";
  }

  private static String rate(BigDecimal value) {
    return value == null ? "no disponible" : value.stripTrailingZeros().toPlainString();
  }

  private static String statusLabel(CaseStatus status) {
    if (status == null) {
      return "sin estado";
    }
    return switch (status) {
      case PRESTUDY -> "Preestudio";
      case DOCUMENTATION -> "Documentación";
      case ANALYSIS -> "Análisis";
      case BANK_SEARCH -> "Búsqueda de banco";
      case BANK_SUBMISSION -> "Envío a banco";
      case BANK_REVIEW -> "Revisión bancaria";
      case OFFER -> "Oferta";
      case FORMALIZATION -> "Formalización";
      case COMPLETED -> "Completada";
      case CANCELLED -> "Cancelada";
    };
  }

  private static String interestTypeLabel(String type) {
    if (type == null) {
      return "no disponible";
    }
    return switch (type) {
      case "FIXED" -> "fijo";
      case "VARIABLE" -> "variable";
      case "MIXED" -> "mixto";
      default -> type;
    };
  }

  private static String ragLabel(RagLevel level) {
    return switch (level) {
      case GREEN -> "Verde";
      case AMBER -> "Ámbar";
      case RED -> "Rojo";
      case NOT_EVALUATED -> "sin evaluar";
    };
  }

  private static String ragAxisLabel(String axis) {
    return switch (axis) {
      case "scoring" -> "scoring";
      case "viability" -> "viabilidad";
      case "documentation" -> "documentación";
      default -> axis;
    };
  }
}
