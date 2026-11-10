package com.aicoach.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Explicit confirmation guards against accidental deletion from a stray request. */
public record DeleteAccountRequest(
        @NotNull @Pattern(regexp = "DELETE", message = "must be exactly DELETE") String confirm) {
}
