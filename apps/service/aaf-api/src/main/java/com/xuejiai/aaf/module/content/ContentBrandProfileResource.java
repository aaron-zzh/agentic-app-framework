package com.xuejiai.aaf.module.content;

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
import com.xuejiai.aaf.module.content.controller.ContentBrandProfileController;
import com.xuejiai.aaf.module.content.domain.ContentBrandProfile;

/**
 * 品牌/IP 资料 CRUD 资源定义。
 *
 * @author AaronZZH & Kiro
 */
public final class ContentBrandProfileResource {

    public static final ResourceKey KEY = ResourceKey.of("content.brand-profile");
    public static final String BASE_PATH = "/api/content/brand-profiles";

    private static final CrudResourceTypeContract<ContentBrandProfile> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    ContentBrandProfileController.class, ContentBrandProfile.class);

    public static final CrudResourceDefinition<ContentBrandProfile> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("品牌/IP 资料", BASE_PATH, "content:brand-profile"),
                    CrudCapabilityDefinition.forTypes(TYPES)
                            .without(
                                    CrudOperation.IMPORT,
                                    CrudOperation.RESTORE,
                                    CrudOperation.ARCHIVE),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.auto(),
                            Set.of(
                                    "name",
                                    "kind",
                                    "industry",
                                    "logoUrl",
                                    "positioning",
                                    "audience",
                                    "toneOfVoice",
                                    "visualStyle",
                                    "disclaimer",
                                    "forbiddenItems",
                                    "rules",
                                    "profileAssets",
                                    "profileVersion",
                                    "status"),
                            Sort.by("id").descending()),
                    CrudMutationDefinition.forTypes(TYPES),
                    CrudViewDefinition.forTypes(TYPES)
                            .withFieldSet(
                                    "list",
                                    Set.of(
                                            "id",
                                            "version",
                                            "name",
                                            "kind",
                                            "industry",
                                            "logoUrl",
                                            "positioning",
                                            "audience",
                                            "toneOfVoice",
                                            "visualStyle",
                                            "disclaimer",
                                            "forbiddenItems",
                                            "rules",
                                            "profileAssets",
                                            "profileVersion",
                                            "status",
                                            "createTime",
                                            "updateTime")),
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.byProperty("ownerId"));

    private ContentBrandProfileResource() {}
}
