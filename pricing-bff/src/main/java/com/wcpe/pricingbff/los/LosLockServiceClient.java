package com.wcpe.pricingbff.los;

import com.wcpe.pricingbff.los.LosApiModels.LockTerms;
import com.wcpe.pricingbff.los.LosApiModels.LosLockRequest;
import com.wcpe.pricingbff.los.LosApiModels.LosLockResponse;
import com.wcpe.pricingbff.los.LosApiModels.LosOffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class LosLockServiceClient {
  LosLockResponse requestLock(LosLockRequest request, LosOffer offer, String correlationId) {
    String seed = request.pricingRequestId() + ":" + request.offerId() + ":" + request.requestedBy();
    String lockId = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    Instant expiration = Instant.now().plus(request.lockPeriodDays() == null ? 30 : request.lockPeriodDays(), ChronoUnit.DAYS);
    String investor = offer == null ? "PENDING" : offer.investor();
    LockTerms terms = offer == null ? new LockTerms(null, null, null) : new LockTerms(offer.rate(), offer.price(), offer.points());
    return new LosLockResponse(lockId, request.pricingRequestId(), request.offerId(), "CONFIRMED", expiration, investor,
        investor + "-LOCK-" + lockId.substring(0, 8).toUpperCase(), terms, correlationId);
  }

  LosLockResponse extendLock(LosLockResponse existing, int extendByDays, String correlationId) {
    return new LosLockResponse(existing.lockId(), existing.pricingRequestId(), existing.offerId(), "EXTENDED",
        existing.lockExpiration().plus(extendByDays, ChronoUnit.DAYS), existing.investor(), existing.investorLockReference(),
        existing.terms(), correlationId);
  }
}
