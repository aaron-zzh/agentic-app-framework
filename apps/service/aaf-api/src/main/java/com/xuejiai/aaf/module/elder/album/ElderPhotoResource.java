package com.xuejiai.aaf.module.elder.album;

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
import com.xuejiai.aaf.module.elder.album.controller.ElderPhotoController;
import com.xuejiai.aaf.module.elder.album.domain.ElderPhoto;

/** 长者照片 CRUD 资源定义。 */
public final class ElderPhotoResource {

    public static final ResourceKey KEY = ResourceKey.of("elder.album.photo");
    public static final String BASE_PATH = "/api/elder/album/photos";

    private static final CrudResourceTypeContract<ElderPhoto> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    ElderPhotoController.class, ElderPhoto.class);

    private static final CrudViewDefinition VIEW =
            CrudViewDefinition.forTypes(TYPES)
                    .withFieldSet(
                            "list",
                            Set.of(
                                    "id",
                                    "version",
                                    "elderId",
                                    "deviceId",
                                    "source",
                                    "capturedAt",
                                    "description",
                                    "thumbnailUrl",
                                    "originalUrl",
                                    "createTime",
                                    "elder"))
                    .withFieldSet("picker", Set.of("id", "capturedAt", "thumbnailUrl"))
                    .withFieldSet(
                            "agent",
                            Set.of(
                                    "id",
                                    "elderId",
                                    "source",
                                    "capturedAt",
                                    "description",
                                    "thumbnailUrl",
                                    "originalUrl"))
                    .withFieldSet(
                            "detail",
                            Set.of(
                                    "id",
                                    "version",
                                    "elderId",
                                    "deviceId",
                                    "clientPhotoId",
                                    "fileId",
                                    "source",
                                    "capturedAt",
                                    "originalName",
                                    "mimeType",
                                    "fileSize",
                                    "contentHash",
                                    "description",
                                    "thumbnailUrl",
                                    "originalUrl",
                                    "createTime",
                                    "updateTime",
                                    "elder",
                                    "createBy",
                                    "updateBy"));

    public static final CrudResourceDefinition<ElderPhoto> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("长者照片", BASE_PATH, "elder:album-photo"),
                    CrudCapabilityDefinition.forTypes(TYPES)
                            .without(
                                    CrudOperation.CREATE,
                                    CrudOperation.VALIDATE,
                                    CrudOperation.IMPORT,
                                    CrudOperation.EXPORT,
                                    CrudOperation.RESTORE,
                                    CrudOperation.ARCHIVE),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.auto(),
                            Set.of("id", "capturedAt", "createTime"),
                            Sort.by("capturedAt").descending().and(Sort.by("id").descending())),
                    CrudMutationDefinition.forTypes(TYPES),
                    VIEW,
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.byProperty("ownerId"));

    private ElderPhotoResource() {}
}
