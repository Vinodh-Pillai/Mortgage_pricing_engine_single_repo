package com.wcpe.tenantcontext.ratelimit;

import com.wcpe.tenantcontext.TenantContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

public class RateLimitCounterKeyBuilder {
    public String key(RateLimitPolicy policy, TenantContext context, Instant now) {
        if (policy == null || context == null) {
            throw new RateLimitPolicyException("RATE_LIMIT_KEY_INVALID", "policy and tenant context are required");
        }
        long windowStart = ((now == null ? Instant.now() : now).getEpochSecond() / policy.windowSeconds()) * policy.windowSeconds();
        return "tenant:" + context.tenantId()
            + ":rate-limit:v" + policy.version()
            + ":actor:" + actorHash(context.actor().actorId())
            + ":endpoint:" + policy.endpointGroup()
            + ":window:" + windowStart;
    }

    private static String actorHash(String actorId) {
        String value = actorId == null ? "" : actorId.trim();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < 8; index++) {
                builder.append(String.format("%02x", hash[index]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new RateLimitPolicyException("RATE_LIMIT_KEY_INVALID", "SHA-256 is unavailable");
        }
    }
}
