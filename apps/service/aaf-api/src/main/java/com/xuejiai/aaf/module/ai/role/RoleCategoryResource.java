package com.xuejiai.aaf.module.ai.role;

import java.util.Set;

import org.springframework.data.domain.Sort;

import com.xuejiai.aaf.framework.crud.definition.CrudCapabilityDefinition;
import com.xuejiai.aaf.framework.crud.definition.CrudMutationDefinition;
import com.xuejiai.aaf.framework.crud.definition.CrudOperation;
import com.xuejiai.aaf.framework.crud.definition.CrudQueryDefinition;
import com.xuejiai.aaf.framework.crud.definition.CrudResourceDefinition;
import com.xuejiai.aaf.framework.crud.definition.CrudResourceDescriptor;
import com.xuejiai.aaf.framework.crud.definition.CrudResourceTypeContract;
import com.xuejiai.aaf.framework.crud.definition.CrudViewDefinition;
import com.xuejiai.aaf.framework.crud.definition.PersonalScope;
import com.xuejiai.aaf.framework.crud.definition.ResourceKey;
import com.xuejiai.aaf.framework.crud.definition.TenantScope;
import com.xuejiai.aaf.framework.crud.filter.CrudFilterSchema;

/**
 * 角色分类 CRUD 资源定义。
 *
 * <p>全局资源，不启用个人范围（个人范围会滤掉系统分类）；默认访问的读取范围由记录规则与服务层查询条件共同限定为系统分类与本人私有分类。
 */
public final class RoleCategoryResource {

    public static final ResourceKey KEY = ResourceKey.of("ai.role-category");
    public static final String BASE_PATH = "/api/ai/role-categories";

    private static final CrudResourceTypeContract<AiRoleCategory> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    RoleCategoryController.class, AiRoleCategory.class);

    public static final CrudResourceDefinition<AiRoleCategory> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("角色分类", BASE_PATH, "system:role-category"),
                    CrudCapabilityDefinition.forTypes(TYPES)
                            .without(
                                    CrudOperation.DELETE_BATCH,
                                    CrudOperation.IMPORT,
                                    CrudOperation.RESTORE,
                                    CrudOperation.ARCHIVE),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.auto(),
                            Set.of(
                                    "id",
                                    "name",
                                    "sortOrder",
                                    "visibility",
                                    "createTime",
                                    "updateTime"),
                            Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("id"))),
                    CrudMutationDefinition.forTypes(TYPES),
                    CrudViewDefinition.forTypes(TYPES),
                    TenantScope.GLOBAL,
                    PersonalScope.none());

    private RoleCategoryResource() {}
}
