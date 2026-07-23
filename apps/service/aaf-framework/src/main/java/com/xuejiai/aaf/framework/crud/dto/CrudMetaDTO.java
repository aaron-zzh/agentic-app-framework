package com.xuejiai.aaf.framework.crud.dto;

import java.util.List;
import java.util.Set;

import com.xuejiai.aaf.framework.crud.definition.CrudOperation;
import com.xuejiai.aaf.framework.crud.definition.CrudResourceExposure;
import com.xuejiai.aaf.framework.crud.definition.TenantScope;
import com.xuejiai.aaf.framework.crud.filter.CrudFilterFieldMeta;

import io.swagger.v3.oas.annotations.media.Schema;

/** 通用 CRUD 元数据。 */
@Schema(description = "通用 CRUD 元数据")
public record CrudMetaDTO(
        @Schema(description = "稳定资源标识") String resourceKey,
        @Schema(description = "实体路由标识") String entitySlug,
        @Schema(description = "实体名称") String entityName,
        @Schema(description = "客户端 API 路径") String apiPath,
        @Schema(description = "权限命名空间") String permissionNamespace,
        @Schema(description = "可用字段集") List<String> fieldSets,
        @Schema(description = "可用通用操作") List<String> operations,
        @Schema(description = "按字段声明的筛选操作符能力") List<CrudFilterFieldMeta> filterFields,
        @Schema(description = "资源允许的排序字段") List<String> sortableFields,
        @Schema(description = "租户范围") TenantScope tenantScope,
        @Schema(description = "资源暴露面") Set<CrudResourceExposure> exposures,
        @Schema(description = "资源契约版本") int schemaVersion,
        @Schema(description = "资源定义指纹") String fingerprint) {
    public CrudMetaDTO {
        fieldSets = List.copyOf(fieldSets);
        operations =
                operations.stream()
                        .map(CrudOperation::fromCapability)
                        .distinct()
                        .map(CrudOperation::capability)
                        .toList();
        filterFields = List.copyOf(filterFields);
        sortableFields = List.copyOf(sortableFields);
        exposures = Set.copyOf(exposures);
    }
}
