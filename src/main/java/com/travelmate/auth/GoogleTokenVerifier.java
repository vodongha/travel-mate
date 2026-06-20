package com.travelmate.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.travelmate.common.exception.ApiException;
import com.travelmate.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

/**
 * Verifies a Google ID token server-side (signature, audience, expiry) and extracts the profile.
 * Configured with {@code app.google.client-id}; when unset, the Google sign-in endpoint reports a
 * clear configuration error rather than silently accepting tokens.
 */
@Component
public class GoogleTokenVerifier {

    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(@Value("${app.google.client-id:}") String clientId) {
        if (clientId == null || clientId.isBlank()) {
            this.verifier = null;
        } else {
            this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(clientId))
                    .build();
        }
    }

    public GoogleUser verify(String idTokenString) {
        if (verifier == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Google sign-in is not configured on the server.");
        }
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new ApiException(ErrorCode.UNAUTHENTICATED, "Invalid Google ID token.");
            }
            GoogleIdToken.Payload payload = idToken.getPayload();
            return new GoogleUser(
                    payload.getEmail(),
                    Boolean.TRUE.equals(payload.getEmailVerified()),
                    (String) payload.get("name"),
                    (String) payload.get("picture"));
        } catch (GeneralSecurityException | IOException e) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Could not verify the Google ID token.", e);
        }
    }

    public record GoogleUser(String email, boolean emailVerified, String name, String picture) {
    }
}
