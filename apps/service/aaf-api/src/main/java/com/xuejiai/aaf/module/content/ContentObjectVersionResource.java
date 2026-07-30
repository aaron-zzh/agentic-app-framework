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
import com.xuejiai.aaf.module.content.controller.ContentObjectVersionController;
import com.xuejiai.aaf.module.content.domain.ContentObjectVersion;

/**
 * 内容对象版本只读 CRUD 资源定义。
 *
 * @author AaronZZH & Kiro
 */
public final class ContentObjectVersionResource {

    public static final ResourceKey KEY = ResourceKey.of("content.object-version");
    public static final String BASE_PATH = "/api/content/object-versions";

    private static final CrudResourceTypeContract<ContentObjectVersion> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    ContentObjectVersionController.class, ContentObjectVersion.class);

    public static final CrudResourceDefinition<ContentObjectVersion> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("内容对象版本", BASE_PATH, "content:object-version"),
                    CrudCapabilityDefinition.forTypes(TYPES)
                            .without(
                                    CrudOperation.IMPORT,
                                    CrudOperation.RESTORE,
                                    CrudOperation.ARCHIVE,
                                    CrudOperation.CREATE,
                                    CrudOperation.UPDATE,
                                    CrudOperation.DELETE,
                                    CrudOperation.DELETE_BATCH),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.empty(),
                            Set.of(
                                    "projectId",
                                    "objectId",
                                    "versionNo",
                                    "status",
                                    "contentPayload",
                                    "assetRefs",
                                    "executionRunId",
                                    "summary",
                                    "adoptedTime",
                                    "supersededByVersionId",
                                    "createTime"),
                            Sort.by("id").descending()),
                    CrudMutationDefinition.forTypes(TYPES),
                    CrudViewDefinition.forTypes(TYPES)
                            .withFieldSet(
                                    "list",
                                    Set.of(
                                            "id",
                                            "version",
                                            "projectId",
                                            "objectId",
                                            "versionNo",
                                            "status",
                                            "contentPayload",
                                            "assetRefs",
                                            "executionRunId",
                                            "summary",
                                            "adoptedTime",
                                            "supersededByVersionId",
                                            "createTime")),
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.byProperty("ownerId"));

    private ContentObjectVersionResource() {}
}
