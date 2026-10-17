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
import com.xuejiai.aaf.module.elder.profile.controller.ElderContactController;
import com.xuejiai.aaf.module.elder.profile.domain.ElderContact;

/** 长者联系人 CRUD 资源定义。 */
public final class ElderContactResource {

    public static final ResourceKey KEY = ResourceKey.of("elder.profile.elder-contact");
    public static final String BASE_PATH = "/api/elder/profile/contacts";

    private static final CrudResourceTypeContract<ElderContact> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    ElderContactController.class, ElderContact.class);

    private static final CrudViewDefinition VIEW =
            CrudViewDefinition.forTypes(TYPES)
                    .withFieldSet(
                            "list",
                            Set.of(
                                    "id",
                                    "version",
                                    "elderId",
                                    "relationship",
                                    "priority",
                                    "emergencyContact",
                                    "createTime",
                                    "elder"))
                    .withFieldSet("picker", Set.of("id", "relationship"))
                    .withFieldSet(
                            "detail",
                            Set.of(
                                    "id",
                                    "version",
                                    "elderId",
                                    "userId",
                                    "name",
                                    "mobile",
                                    "relationship",
                                    "priority",
                                    "emergencyContact",
                                    "authorizationScopes",
                                    "createTime",
                                    "updateTime",
                                    "elder",
                                    "user",
                                    "createBy",
                                    "updateBy"))
                    .withFieldSet(
                            "export",
                            Set.of(
                                    "id",
                                    "version",
                                    "elderId",
                                    "relationship",
                                    "priority",
                                    "emergencyContact",
                                    "createTime"));

    public static final CrudResourceDefinition<ElderContact> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("长者联系人", BASE_PATH, "elder:profile-elder-contact"),
                    CrudCapabilityDefinition.forTypes(TYPES)
                            .without(
                                    CrudOperation.IMPORT,
                                    CrudOperation.RESTORE,
                                    CrudOperation.ARCHIVE),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.auto(),
                            Set.of("id", "priority", "createTime"),
                            Sort.by("priority").ascending().and(Sort.by("id").ascending())),
                    CrudMutationDefinition.forTypes(TYPES),
                    VIEW,
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.none());

    private ElderContactResource() {}
}
