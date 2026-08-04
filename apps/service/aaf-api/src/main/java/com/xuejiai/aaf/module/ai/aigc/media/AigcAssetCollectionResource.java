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
import com.xuejiai.aaf.module.ai.aigc.media.controller.AigcAssetCollectionController;
import com.xuejiai.aaf.module.ai.aigc.media.domain.AigcAssetCollection;

/** AIGC 资产集合 CRUD 资源定义。集合成员由独立聚合命令维护。 */
public final class AigcAssetCollectionResource {

    public static final ResourceKey KEY = ResourceKey.of("aigc.asset-collection");
    public static final String BASE_PATH = "/api/aigc/asset-collections";

    private static final CrudResourceTypeContract<AigcAssetCollection> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    AigcAssetCollectionController.class, AigcAssetCollection.class);

    public static final CrudResourceDefinition<AigcAssetCollection> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("资产集合", BASE_PATH, "aigc:asset-collection"),
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
                                    "collectionType",
                                    "description",
                                    "createTime",
                                    "updateTime"),
                            Sort.by("id").descending()),
                    CrudMutationDefinition.forTypes(TYPES),
                    CrudViewDefinition.forTypes(TYPES),
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.byProperty("ownerId"));

    private AigcAssetCollectionResource() {}
}
