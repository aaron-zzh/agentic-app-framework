package com.xuejiai.aaf.module.system.entity.vo;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.xuejiai.aaf.framework.crud.definition.CrudResourceExposure;
import com.xuejiai.aaf.framework.crud.definition.TenantScope;

import io.swagger.v3.oas.annotations.media.Schema;

/** 受信任代码资源对前端公开的 Catalog 快照。 */
@Schema(description = "代码资源描述")
public record CodeEntityResourceVO(
        @Schema(description = "稳定资源标识", example = "system.user") String resource,
        @Schema(description = "实体路由标识", example = "user") String slug,
        @Schema(description = "资源名称") String label,
        @Schema(description = "客户端 API 路径", example = "/system/users") String apiPath,
        @Schema(description = "权限命名空间") String permissionNamespace,
        @Schema(description = "EntityDef 可引用的展示 VO 字段") List<String> fields,
        @Schema(description = "资源操作能力") List<String> capabilities,
        @Schema(description = "资源字段集") List<String> fieldSets,
        @Schema(description = "租户范围") TenantScope tenantScope,
        @Schema(description = "资源暴露面") Set<CrudResourceExposure> exposures,
        @Schema(description = "资源契约版本") int schemaVersion,
        @Schema(description = "是否为可引用的完整 CRUD 资源") boolean referenceable,
        @Schema(description = "资源定义指纹") String fingerprint,
        @Schema(description = "Catalog 构建时间") Instant builtAt) {
    public CodeEntityResourceVO {
        fields = List.copyOf(fields);
        capabilities = List.copyOf(capabilities);
        fieldSets = List.copyOf(fieldSets);
        exposures = Set.copyOf(exposures);
    }
}
