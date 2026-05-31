package com.xuejiai.aaf.module.developer.vo;

import jakarta.validation.constraints.NotNull;

public record DeveloperProxyCreateDTO(
        @NotNull Long childDeveloperId, Long tokenLimit, Boolean allowSubProxy) {}
