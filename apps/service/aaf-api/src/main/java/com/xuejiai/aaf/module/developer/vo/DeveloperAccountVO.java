package com.xuejiai.aaf.module.developer.vo;

public record DeveloperAccountVO(
        Long id,
        String developerCode,
        Long userId,
        String name,
        String status,
        String licenseTier,
        Boolean allowManagedGateway,
        Boolean allowSubProxy,
        Integer maxProxyDepth) {}
