package com.aicoach.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 254) @Email(regexp = ".+@.+\\..+", message = "must be a valid email address") String email,
        @NotNull @Size(min = 10, max = 128)
        @Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d).*$", message = "must contain at least one letter and one digit")
        String password,
        @NotBlank @Size(max = 80) String name) {

    /** Whitespace around email and name is never meaningful; trim before validation. */
    public RegisterRequest {
        email = email == null ? null : email.trim();
        name = name == null ? null : name.trim();
    }

    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", password=***, name=" + name + "]";
    }
}
