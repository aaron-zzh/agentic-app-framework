package com.xuejiai.aaf.module.developer.vo;

import jakarta.validation.constraints.NotBlank;

public record DeveloperApiKeyCreateDTO(@NotBlank String name, Integer expiresInDays, String scopes) {}
