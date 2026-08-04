package com.xuejiai.aaf.module.ai.aigc.media;

import java.util.List;
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
import com.xuejiai.aaf.module.ai.aigc.media.controller.AssetController;
import com.xuejiai.aaf.module.ai.aigc.media.domain.Asset;

/** AIGC Asset CRUD 资源定义。 */
public final class AssetResource {

    public static final ResourceKey KEY = ResourceKey.of("aigc.asset");
    public static final String BASE_PATH = "/api/aigc/assets";

    private static final CrudResourceTypeContract<Asset> TYPES =
            CrudResourceTypeContract.fromCrudController(AssetController.class, Asset.class);

    public static final CrudResourceDefinition<Asset> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("资产", BASE_PATH, "aigc:asset"),
                    new CrudCapabilityDefinition(
                            List.of(
                                    CrudOperation.PAGE,
                                    CrudOperation.GET,
                                    CrudOperation.UPDATE,
                                    CrudOperation.DELETE)),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.auto(),
                            Set.of(
                                    "id",
                                    "categoryId",
                                    "scope",
                                    "status",
                                    "usageCount",
                                    "createTime"),
                            Sort.by("createTime").descending()),
                    CrudMutationDefinition.forTypes(TYPES),
                    CrudViewDefinition.forTypes(TYPES),
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.byProperty("ownerId"));

    private AssetResource() {}
}
