package com.xuejiai.aaf.module.knowledge;

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
import com.xuejiai.aaf.module.knowledge.controller.KnowledgeDocumentReferenceController;
import com.xuejiai.aaf.module.knowledge.domain.KnowledgeDocument;

/** 知识文档只读引用资源；文档写操作仍由知识库聚合入口管理。 */
public final class KnowledgeDocumentResource {

    public static final ResourceKey KEY = ResourceKey.of("knowledge.knowledge-document");
    public static final String BASE_PATH = "/api/knowledge-documents";

    private static final CrudResourceTypeContract<KnowledgeDocument> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    KnowledgeDocumentReferenceController.class, KnowledgeDocument.class);

    public static final CrudResourceDefinition<KnowledgeDocument> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("知识文档", BASE_PATH, "system:knowledge-base"),
                    CrudCapabilityDefinition.forTypes(TYPES)
                            .without(
                                    CrudOperation.CREATE,
                                    CrudOperation.UPDATE,
                                    CrudOperation.DELETE,
                                    CrudOperation.DELETE_BATCH,
                                    CrudOperation.GROUP,
                                    CrudOperation.IMPORT,
                                    CrudOperation.VALIDATE,
                                    CrudOperation.RESTORE,
                                    CrudOperation.ARCHIVE),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.auto(),
                            Set.of("id", "title", "knowledgeBaseId", "status", "createTime"),
                            Sort.by("id").descending()),
                    CrudMutationDefinition.forTypes(TYPES),
                    CrudViewDefinition.forTypes(TYPES)
                            .withFieldSet(
                                    "list",
                                    Set.of(
                                            "id",
                                            "title",
                                            "knowledgeBaseId",
                                            "sourceType",
                                            "status",
                                            "createTime"))
                            .withFieldSet("picker", Set.of("id", "title", "status"))
                            .withFieldSet(
                                    "detail",
                                    Set.of(
                                            "id",
                                            "stableId",
                                            "knowledgeBaseId",
                                            "sourceType",
                                            "sourceKey",
                                            "sourceUri",
                                            "title",
                                            "fileType",
                                            "fileSize",
                                            "status",
                                            "chunkCount",
                                            "createTime",
                                            "updateTime")),
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.byProperty("ownerId"));

    private KnowledgeDocumentResource() {}
}
