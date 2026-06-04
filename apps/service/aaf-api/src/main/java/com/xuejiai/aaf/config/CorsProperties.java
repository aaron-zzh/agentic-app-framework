package com.xuejiai.aaf.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** CORS 白名单配置。 */
@ConfigurationProperties(prefix = "aaf.cors")
public record CorsProperties(
        List<String> allowedOriginPatterns,
        List<String> allowedMethods,
        List<String> allowedHeaders,
        Boolean allowCredentials,
        Long maxAge) {

    public CorsProperties {
        allowedOriginPatterns =
                allowedOriginPatterns == null || allowedOriginPatterns.isEmpty()
                        ? List.of("http://localhost:3000", "http://127.0.0.1:3000")
                        : List.copyOf(allowedOriginPatterns);
        allowedMethods =
                allowedMethods == null || allowedMethods.isEmpty()
                        ? List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        : List.copyOf(allowedMethods);
        allowedHeaders =
                allowedHeaders == null || allowedHeaders.isEmpty()
                        ? List.of("*")
                        : List.copyOf(allowedHeaders);
        allowCredentials = allowCredentials == null ? Boolean.TRUE : allowCredentials;
        maxAge = maxAge == null ? 3600L : maxAge;
    }
}
