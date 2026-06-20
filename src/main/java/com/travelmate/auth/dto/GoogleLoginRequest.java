package com.travelmate.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Carries the Google ID token obtained client-side by the Flutter app. */
public record GoogleLoginRequest(@NotBlank String idToken) {
}
