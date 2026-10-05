package com.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotBlank @Size(min = 4, max = 20) String cin,
        @NotBlank @Size(min = 8, max = 20) String phone) {}
