package com.wcpe.eligibility.domain.hashing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class Hashing {
    private Hashing() {}

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder("sha256:");
            for (byte b : digest) out.append(String.format("%02x", b));
            return out.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
