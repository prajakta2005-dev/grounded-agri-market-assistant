package com.groundedagri.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
public class SourceAdminAuthorization {
    private final String expectedToken;

    public SourceAdminAuthorization(@Value("${app.source-admin.token:}") String expectedToken) {
        this.expectedToken = expectedToken == null ? "" : expectedToken;
    }

    public void requireToken(String suppliedToken) {
        if (!StringUtils.hasText(expectedToken)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Set SOURCE_ADMIN_TOKEN in the project .env to enable source management.");
        }
        if (!StringUtils.hasText(suppliedToken) || suppliedToken.length() > 512
                || !constantTimeEquals(expectedToken, suppliedToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The source manager token is not valid.");
        }
    }

    private boolean constantTimeEquals(String expected, String supplied) {
        try {
            byte[] expectedHash = MessageDigest.getInstance("SHA-256")
                    .digest(expected.getBytes(StandardCharsets.UTF_8));
            byte[] suppliedHash = MessageDigest.getInstance("SHA-256")
                    .digest(supplied.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(expectedHash, suppliedHash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
