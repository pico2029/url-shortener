package com.example.shortener.security;

import com.example.shortener.auth.LoginResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
    public static final String ISSUER = "url-shortener";
    private final JwtEncoder encoder;

    public TokenService(JwtEncoder encoder) { this.encoder = encoder; }

    public LoginResponse issue(UUID userId) {
        Instant now = Instant.now();
        Instant expiry = now.plus(1, ChronoUnit.HOURS);
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(ISSUER).subject(userId.toString())
                .issuedAt(now).expiresAt(expiry).build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new LoginResponse(token, "Bearer", expiry);
    }
}
