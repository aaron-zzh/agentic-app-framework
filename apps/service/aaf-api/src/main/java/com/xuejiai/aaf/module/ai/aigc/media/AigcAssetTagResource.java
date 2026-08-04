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
import com.xuejiai.aaf.module.ai.aigc.media.controller.AigcAssetTagController;
import com.xuejiai.aaf.module.ai.aigc.media.domain.AigcAssetTag;

/** AIGC 资产标签 CRUD 资源定义。 */
public final class AigcAssetTagResource {

    public static final ResourceKey KEY = ResourceKey.of("aigc.asset-tag");
    public static final String BASE_PATH = "/api/aigc/asset-tags";

    private static final CrudResourceTypeContract<AigcAssetTag> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    AigcAssetTagController.class, AigcAssetTag.class);

    public static final CrudResourceDefinition<AigcAssetTag> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("资产标签", BASE_PATH, "aigc:asset-tag"),
                    CrudCapabilityDefinition.forTypes(TYPES)
                            .without(
                                    CrudOperation.DELETE_BATCH,
                                    CrudOperation.IMPORT,
                                    CrudOperation.RESTORE,
                                    CrudOperation.ARCHIVE),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.auto(),
                            Set.of("id", "name", "color", "usageCount", "createTime", "updateTime"),
                            Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"))),
                    CrudMutationDefinition.forTypes(TYPES),
                    CrudViewDefinition.forTypes(TYPES),
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.none());

    private AigcAssetTagResource() {}
}
