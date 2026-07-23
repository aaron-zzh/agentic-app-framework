package com.xuejiai.aaf.framework.crud.definition;

import java.util.Objects;
import java.util.regex.Pattern;

/** 资源标签、HTTP 路径与权限命名空间的稳定描述。 */
public record CrudResourceDescriptor(String label, String apiPath, String permissionNamespace) {

    private static final Pattern PERMISSION_NAMESPACE_PATTERN =
            Pattern.compile("^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$");

    public CrudResourceDescriptor {
        label = requireText(label, "label");
        apiPath = requireText(apiPath, "apiPath");
        permissionNamespace = requireText(permissionNamespace, "permissionNamespace");
        if (!apiPath.startsWith("/api/") || apiPath.length() == "/api".length()) {
            throw new IllegalArgumentException("资源 API 路径必须以 /api/ 开头: " + apiPath);
        }
        if (!PERMISSION_NAMESPACE_PATTERN.matcher(permissionNamespace).matches()) {
            throw new IllegalArgumentException("非法 CRUD 权限命名空间: " + permissionNamespace);
        }
    }

    public String clientApiPath() {
        return apiPath.substring("/api".length());
    }

    private static String requireText(String value, String name) {
        var normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return normalized;
    }
}
