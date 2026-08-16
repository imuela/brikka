package com.brika.platform.bankmatching;

import com.brika.platform.financing.FinancingRequest;
import com.brika.platform.financing.FinancingRequestRepository;
import com.brika.platform.property.Property;
import com.brika.platform.property.PropertyRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * ADR-BANKENGINE-001 §1/D-A/D-C: builds the snapshot server-side from the case's existing data —
 * never accepted from the client (D-C). financing_requests has no UNIQUE(case_id), so the most
 * recently created one is used (findAllByCaseId is already ordered created_at DESC) — same "most
 * recent wins" convention already used for Sprint 7's conversation resolution.
 *
 * <p>D-A: ltv = requestedAmount / MIN(valuation, purchasePrice) when both exist; falls back to
 * whichever single denominator exists; null if neither exists, if requestedAmount is null (no
 * FinancingRequest), or if the denominator is zero (division is not meaningful).
 */
@Component
public class InputSnapshotFactory {

  private static final int LTV_SCALE = 4;

  private final PropertyRepository propertyRepository;
  private final FinancingRequestRepository financingRequestRepository;

  public InputSnapshotFactory(
      PropertyRepository propertyRepository,
      FinancingRequestRepository financingRequestRepository) {
    this.propertyRepository = propertyRepository;
    this.financingRequestRepository = financingRequestRepository;
  }

  public InputSnapshot build(UUID caseId) {
    Optional<Property> property = propertyRepository.findByCaseId(caseId);
    Optional<FinancingRequest> financingRequest = mostRecentFinancingRequest(caseId);

    BigDecimal requestedAmount =
        financingRequest.map(FinancingRequest::requestedAmount).orElse(null);
    BigDecimal termMonths =
        financingRequest.map(fr -> BigDecimal.valueOf(fr.termMonths())).orElse(null);
    BigDecimal ltv = computeLtv(requestedAmount, property.orElse(null));

    return new InputSnapshot(ltv, requestedAmount, termMonths);
  }

  private Optional<FinancingRequest> mostRecentFinancingRequest(UUID caseId) {
    List<FinancingRequest> requests = financingRequestRepository.findAllByCaseId(caseId);
    return requests.stream().findFirst();
  }

  private BigDecimal computeLtv(BigDecimal requestedAmount, Property property) {
    if (requestedAmount == null || property == null) {
      return null;
    }
    BigDecimal valuation = property.valuation();
    BigDecimal purchasePrice = property.purchasePrice();

    BigDecimal denominator;
    if (valuation != null && purchasePrice != null) {
      denominator = valuation.min(purchasePrice);
    } else if (valuation != null) {
      denominator = valuation;
    } else if (purchasePrice != null) {
      denominator = purchasePrice;
    } else {
      return null;
    }

    if (denominator.compareTo(BigDecimal.ZERO) == 0) {
      return null;
    }
    return requestedAmount.divide(denominator, LTV_SCALE, RoundingMode.HALF_UP);
  }
}
