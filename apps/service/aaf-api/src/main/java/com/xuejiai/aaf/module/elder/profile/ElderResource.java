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
import com.xuejiai.aaf.module.elder.profile.controller.ElderController;
import com.xuejiai.aaf.module.elder.profile.domain.Elder;

/** 长者档案 CRUD 资源定义。 */
public final class ElderResource {

    public static final ResourceKey KEY = ResourceKey.of("elder.profile.elder");
    public static final String BASE_PATH = "/api/elder/profile/elders";

    private static final CrudResourceTypeContract<Elder> TYPES =
            CrudResourceTypeContract.fromCrudController(ElderController.class, Elder.class);

    private static final CrudViewDefinition VIEW =
            CrudViewDefinition.forTypes(TYPES)
                    .withFieldSet(
                            "list",
                            Set.of(
                                    "id",
                                    "version",
                                    "preferredName",
                                    "gender",
                                    "livingArrangement",
                                    "status",
                                    "cameraConsent",
                                    "createTime"))
                    .withFieldSet("picker", Set.of("id", "preferredName"))
                    .withFieldSet(
                            "detail",
                            Set.of(
                                    "id",
                                    "version",
                                    "userId",
                                    "fullName",
                                    "gender",
                                    "birthDate",
                                    "idCardNo",
                                    "mobile",
                                    "address",
                                    "livingArrangement",
                                    "preferredName",
                                    "preferences",
                                    "taboos",
                                    "status",
                                    "cameraConsent",
                                    "cameraConsentBy",
                                    "cameraConsentAt",
                                    "createTime",
                                    "updateTime",
                                    "user",
                                    "cameraConsentUser",
                                    "contacts",
                                    "healthItems",
                                    "medicationPlans",
                                    "medicationRecords",
                                    "createBy",
                                    "updateBy"))
                    .withFieldSet(
                            "export",
                            Set.of(
                                    "id",
                                    "version",
                                    "preferredName",
                                    "gender",
                                    "livingArrangement",
                                    "status",
                                    "cameraConsent",
                                    "createTime"));

    public static final CrudResourceDefinition<Elder> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("长者", BASE_PATH, "elder:profile-elder"),
                    CrudCapabilityDefinition.forTypes(TYPES)
                            .without(
                                    CrudOperation.IMPORT,
                                    CrudOperation.RESTORE,
                                    CrudOperation.ARCHIVE),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.auto(),
                            Set.of("id", "preferredName", "birthDate", "createTime"),
                            Sort.by("id").descending()),
                    CrudMutationDefinition.forTypes(TYPES),
                    VIEW,
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.byProperty("userId"));

    private ElderResource() {}
}
