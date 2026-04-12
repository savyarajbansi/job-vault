package com.project8.jobvault.auth;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class TokenHasher {
    private final SecretKeySpec keySpec;

    public TokenHasher(JwtProperties properties) {
        this.keySpec = new SecretKeySpec(properties.getRefreshHashSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public String hash(String token) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(keySpec);
            byte[] digest = mac.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("Unable to hash refresh token", ex);
        }
    }
}
