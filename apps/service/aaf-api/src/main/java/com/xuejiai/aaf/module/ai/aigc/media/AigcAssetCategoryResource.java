package com.xuejiai.aaf.module.ai.aigc.media;

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
import com.xuejiai.aaf.module.ai.aigc.media.controller.AigcAssetCategoryController;
import com.xuejiai.aaf.module.ai.aigc.media.domain.AigcAssetCategory;

/** AIGC 资产分类 CRUD 资源定义。 */
public final class AigcAssetCategoryResource {

    public static final ResourceKey KEY = ResourceKey.of("aigc.asset-category");
    public static final String BASE_PATH = "/api/aigc/asset-categories";

    private static final CrudResourceTypeContract<AigcAssetCategory> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    AigcAssetCategoryController.class, AigcAssetCategory.class);

    public static final CrudResourceDefinition<AigcAssetCategory> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("资产分类", BASE_PATH, "aigc:asset-category"),
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
                                    "parentId",
                                    "sortOrder",
                                    "createTime",
                                    "updateTime"),
                            Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("id"))),
                    CrudMutationDefinition.forTypes(TYPES),
                    CrudViewDefinition.forTypes(TYPES),
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.byProperty("ownerId"));

    private AigcAssetCategoryResource() {}
}
