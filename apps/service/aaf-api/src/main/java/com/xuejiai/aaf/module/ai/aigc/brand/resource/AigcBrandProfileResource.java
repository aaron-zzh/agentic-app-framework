package com.xuejiai.aaf.module.ai.aigc.brand.resource;

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
import com.xuejiai.aaf.module.ai.aigc.brand.controller.AigcBrandProfileController;
import com.xuejiai.aaf.module.ai.aigc.brand.domain.AigcBrandProfile;

/** AIGC 品牌/IP 资料根 CRUD 资源定义。 */
public final class AigcBrandProfileResource {

    public static final ResourceKey KEY = ResourceKey.of("aigc.brand-profile");
    public static final String BASE_PATH = "/api/aigc/brand-profiles";

    private static final CrudResourceTypeContract<AigcBrandProfile> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    AigcBrandProfileController.class, AigcBrandProfile.class);

    public static final CrudResourceDefinition<AigcBrandProfile> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("品牌/IP 资料", BASE_PATH, "aigc:brand-profile"),
                    CrudCapabilityDefinition.forTypes(TYPES)
                            .without(
                                    CrudOperation.IMPORT,
                                    CrudOperation.RESTORE,
                                    CrudOperation.ARCHIVE),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.auto(),
                            Set.of("name", "kind", "industry", "currentVersionId", "status"),
                            Sort.by("updateTime").descending()),
                    CrudMutationDefinition.forTypes(TYPES),
                    CrudViewDefinition.forTypes(TYPES),
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.byProperty("ownerId"));

    private AigcBrandProfileResource() {}
}
