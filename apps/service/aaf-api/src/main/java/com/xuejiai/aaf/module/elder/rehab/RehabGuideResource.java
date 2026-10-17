package com.xuejiai.aaf.module.elder.rehab;

import java.util.Map;
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
import com.xuejiai.aaf.module.elder.rehab.controller.RehabGuideController;
import com.xuejiai.aaf.module.elder.rehab.domain.RehabGuide;

/** 康复指导 CRUD 资源定义。 */
public final class RehabGuideResource {

    public static final ResourceKey KEY = ResourceKey.of("elder.rehab.guide");
    public static final String BASE_PATH = "/api/elder/rehab/guides";
    public static final String COMMAND_PUBLISH = "REHAB_GUIDE_PUBLISH";
    public static final String COMMAND_OFFLINE = "REHAB_GUIDE_OFFLINE";

    private static final CrudResourceTypeContract<RehabGuide> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    RehabGuideController.class, RehabGuide.class);

    private static final CrudViewDefinition VIEW =
            CrudViewDefinition.forTypes(TYPES)
                    .withFieldSet(
                            "list",
                            Set.of(
                                    "id",
                                    "version",
                                    "title",
                                    "category",
                                    "summary",
                                    "knowledgeDocumentId",
                                    "status",
                                    "createTime",
                                    "knowledgeDocument"))
                    .withFieldSet("picker", Set.of("id", "title", "category", "status"))
                    .withFieldSet(
                            "detail",
                            Set.of(
                                    "id",
                                    "version",
                                    "title",
                                    "category",
                                    "summary",
                                    "applicableAudience",
                                    "knowledgeDocumentId",
                                    "status",
                                    "createTime",
                                    "updateTime",
                                    "knowledgeDocument",
                                    "createBy",
                                    "updateBy"))
                    .withFieldSet(
                            "export",
                            Set.of(
                                    "id",
                                    "title",
                                    "category",
                                    "summary",
                                    "applicableAudience",
                                    "knowledgeDocumentId",
                                    "status",
                                    "createTime"));

    public static final CrudResourceDefinition<RehabGuide> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("康复指导", BASE_PATH, "elder:rehab-guide"),
                    CrudCapabilityDefinition.forTypes(TYPES)
                            .without(
                                    CrudOperation.IMPORT,
                                    CrudOperation.RESTORE,
                                    CrudOperation.ARCHIVE),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.auto(),
                            Set.of("id", "title", "category", "status", "createTime"),
                            Sort.by("id").descending()),
                    CrudMutationDefinition.forTypes(TYPES)
                            .withCustomUpdateCommands(
                                    Map.of(
                                            COMMAND_PUBLISH, Set.of("status"),
                                            COMMAND_OFFLINE, Set.of("status"))),
                    VIEW,
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.none());

    private RehabGuideResource() {}
}
