package com.xuejiai.aaf.module.ai.credential.vo;

import java.time.Instant;
import java.util.Set;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ConnectorCredentialRegisterDTO(
        @NotBlank String connectorId,
        @NotEmpty Set<@NotBlank String> scopes,
        @NotNull @Future Instant expiresAt,
        @NotBlank
                @Pattern(
                        regexp = "^(?:[A-Za-z][A-Za-z0-9+.-]*://\\S+|arn:[A-Za-z0-9:/_.-]+)$",
                        message = "vaultRef 必须是 Vault URI 或 ARN")
                String vaultRef) {}
