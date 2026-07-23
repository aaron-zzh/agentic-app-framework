package com.xuejiai.aaf.framework.crud.resource;

import java.util.Objects;

import com.xuejiai.aaf.framework.crud.definition.ResourceKey;

/** 资源与受信任 Controller 的端点绑定。 */
public record CrudResourceEndpointBinding(
        ResourceKey resourceKey,
        Class<?> controllerType,
        String apiPath,
        CrudResourceEndpointKind kind) {

    public CrudResourceEndpointBinding {
        resourceKey = Objects.requireNonNull(resourceKey, "resourceKey");
        controllerType = Objects.requireNonNull(controllerType, "controllerType");
        apiPath = Objects.requireNonNull(apiPath, "apiPath").trim();
        kind = Objects.requireNonNull(kind, "kind");
        if (!apiPath.startsWith("/api/") || apiPath.length() == "/api".length()) {
            throw new IllegalArgumentException("资源 API 路径必须以 /api/ 开头: " + apiPath);
        }
    }

    public static CrudResourceEndpointBinding crud(
            ResourceKey resourceKey, Class<?> controllerType, String apiPath) {
        return new CrudResourceEndpointBinding(
                resourceKey, controllerType, apiPath, CrudResourceEndpointKind.CRUD);
    }

    public static CrudResourceEndpointBinding nestedCrud(
            ResourceKey resourceKey, Class<?> controllerType, String apiPath) {
        return new CrudResourceEndpointBinding(
                resourceKey, controllerType, apiPath, CrudResourceEndpointKind.NESTED_CRUD);
    }

    public static CrudResourceEndpointBinding options(
            ResourceKey resourceKey, Class<?> controllerType, String apiPath) {
        return new CrudResourceEndpointBinding(
                resourceKey, controllerType, apiPath, CrudResourceEndpointKind.OPTIONS);
    }
}
