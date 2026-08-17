package com.brika.platform.scoring;

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
 * ADR-SCORING-001: builds the snapshot server-side — never accepted from the client. Deliberately
 * does not import com.brika.platform.bankmatching (no cross-domain coupling); the LTV formula is
 * reimplemented here identically to ADR-BANKENGINE-001 D-A: ltv = requestedAmount / MIN(valuation,
 * purchasePrice), falling back to whichever single denominator exists, null if neither exists or if
 * requestedAmount is null, scale 4, HALF_UP. financing_requests has no UNIQUE(case_id), so the most
 * recently created one is used (same "most recent wins" convention already used elsewhere in this
 * project).
 */
@Component
public class ScoreInputSnapshotFactory {

  private static final int LTV_SCALE = 4;

  private final PropertyRepository propertyRepository;
  private final FinancingRequestRepository financingRequestRepository;

  public ScoreInputSnapshotFactory(
      PropertyRepository propertyRepository,
      FinancingRequestRepository financingRequestRepository) {
    this.propertyRepository = propertyRepository;
    this.financingRequestRepository = financingRequestRepository;
  }

  public ScoreInputSnapshot build(UUID caseId) {
    Optional<Property> property = propertyRepository.findByCaseId(caseId);
    Optional<FinancingRequest> financingRequest = mostRecentFinancingRequest(caseId);

    BigDecimal requestedAmount =
        financingRequest.map(FinancingRequest::requestedAmount).orElse(null);
    BigDecimal termMonths =
        financingRequest.map(fr -> BigDecimal.valueOf(fr.termMonths())).orElse(null);
    BigDecimal valuation = property.map(Property::valuation).orElse(null);
    BigDecimal purchasePrice = property.map(Property::purchasePrice).orElse(null);
    BigDecimal ltv = computeLtv(requestedAmount, valuation, purchasePrice);

    return new ScoreInputSnapshot(termMonths, requestedAmount, valuation, purchasePrice, ltv);
  }

  private Optional<FinancingRequest> mostRecentFinancingRequest(UUID caseId) {
    List<FinancingRequest> requests = financingRequestRepository.findAllByCaseId(caseId);
    return requests.stream().findFirst();
  }

  private BigDecimal computeLtv(
      BigDecimal requestedAmount, BigDecimal valuation, BigDecimal purchasePrice) {
    if (requestedAmount == null) {
      return null;
    }

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
