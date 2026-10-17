package com.xuejiai.aaf.module.elder.profile;

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
import com.xuejiai.aaf.module.elder.profile.controller.ElderHealthItemController;
import com.xuejiai.aaf.module.elder.profile.domain.ElderHealthItem;

/** 长者健康条目 CRUD 资源定义。 */
public final class ElderHealthItemResource {

    public static final ResourceKey KEY = ResourceKey.of("elder.profile.health-item");
    public static final String BASE_PATH = "/api/elder/profile/health-items";

    private static final CrudResourceTypeContract<ElderHealthItem> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    ElderHealthItemController.class, ElderHealthItem.class);

    private static final CrudViewDefinition VIEW =
            CrudViewDefinition.forTypes(TYPES)
                    .withFieldSet(
                            "list",
                            Set.of(
                                    "id",
                                    "version",
                                    "elderId",
                                    "itemType",
                                    "name",
                                    "occurredDate",
                                    "createTime",
                                    "elder"))
                    .withFieldSet("picker", Set.of("id", "name", "itemType"))
                    .withFieldSet(
                            "detail",
                            Set.of(
                                    "id",
                                    "version",
                                    "elderId",
                                    "itemType",
                                    "name",
                                    "description",
                                    "occurredDate",
                                    "createTime",
                                    "updateTime",
                                    "elder",
                                    "createBy",
                                    "updateBy"))
                    .withFieldSet(
                            "export",
                            Set.of(
                                    "id",
                                    "elderId",
                                    "itemType",
                                    "name",
                                    "occurredDate",
                                    "createTime"));

    public static final CrudResourceDefinition<ElderHealthItem> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("健康条目", BASE_PATH, "elder:profile-health-item"),
                    CrudCapabilityDefinition.forTypes(TYPES)
                            .without(
                                    CrudOperation.IMPORT,
                                    CrudOperation.RESTORE,
                                    CrudOperation.ARCHIVE),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.auto(),
                            Set.of("id", "name", "occurredDate", "createTime"),
                            Sort.by("occurredDate").descending().and(Sort.by("id").descending())),
                    CrudMutationDefinition.forTypes(TYPES),
                    VIEW,
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.none());

    private ElderHealthItemResource() {}
}
