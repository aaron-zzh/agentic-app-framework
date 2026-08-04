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
import com.xuejiai.aaf.module.content.controller.ContentProjectController;
import com.xuejiai.aaf.module.content.domain.ContentProject;

/**
 * 内容项目 CRUD 资源定义。
 *
 * @author AaronZZH & Kiro
 */
public final class ContentProjectResource {

    public static final ResourceKey KEY = ResourceKey.of("content.project");
    public static final String BASE_PATH = "/api/content/projects";

    private static final CrudResourceTypeContract<ContentProject> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    ContentProjectController.class, ContentProject.class);

    public static final CrudResourceDefinition<ContentProject> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("内容项目", BASE_PATH, "content:project"),
                    CrudCapabilityDefinition.forTypes(TYPES)
                            .without(
                                    CrudOperation.IMPORT,
                                    CrudOperation.RESTORE,
                                    CrudOperation.ARCHIVE,
                                    CrudOperation.CREATE,
                                    CrudOperation.DELETE_BATCH),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.auto(),
                            Set.of(
                                    "name",
                                    "projectTypeCode",
                                    "blueprintCode",
                                    "blueprintVersion",
                                    "domainExtensionCode",
                                    "domainExtensionVersion",
                                    "productionMode",
                                    "generationMode",
                                    "status",
                                    "brief",
                                    "coverUrl",
                                    "channels",
                                    "configSnapshot",
                                    "graphRevision",
                                    "primaryBrandProfileId",
                                    "assistantId",
                                    "budgetLimit",
                                    "costUsed",
                                    "lastActiveTime"),
                            Sort.by("id").descending()),
                    CrudMutationDefinition.forTypes(TYPES),
                    CrudViewDefinition.forTypes(TYPES)
                            .withFieldSet(
                                    "list",
                                    Set.of(
                                            "id",
                                            "version",
                                            "name",
                                            "projectTypeCode",
                                            "blueprintCode",
                                            "blueprintVersion",
                                            "domainExtensionCode",
                                            "domainExtensionVersion",
                                            "productionMode",
                                            "generationMode",
                                            "status",
                                            "brief",
                                            "coverUrl",
                                            "channels",
                                            "graphRevision",
                                            "primaryBrandProfileId",
                                            "primaryBrandProfileName",
                                            "budgetLimit",
                                            "costUsed",
                                            "lastActiveTime",
                                            "createTime",
                                            "updateTime")),
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.byProperty("ownerId"));

    private ContentProjectResource() {}
}
