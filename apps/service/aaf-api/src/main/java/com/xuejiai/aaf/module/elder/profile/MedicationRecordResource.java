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
import com.xuejiai.aaf.module.elder.profile.controller.MedicationRecordController;
import com.xuejiai.aaf.module.elder.profile.domain.MedicationRecord;

/** 服药事实记录资源定义；只开放新增与读取能力。 */
public final class MedicationRecordResource {

    public static final ResourceKey KEY = ResourceKey.of("elder.profile.medication-record");
    public static final String BASE_PATH = "/api/elder/profile/medication-records";

    private static final CrudResourceTypeContract<MedicationRecord> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    MedicationRecordController.class, MedicationRecord.class);

    private static final CrudViewDefinition VIEW =
            CrudViewDefinition.forTypes(TYPES)
                    .withFieldSet(
                            "list",
                            Set.of(
                                    "id",
                                    "elderId",
                                    "planId",
                                    "scheduledAt",
                                    "result",
                                    "source",
                                    "recordedAt",
                                    "plan",
                                    "elder"))
                    .withFieldSet("picker", Set.of("id", "scheduledAt", "result"))
                    .withFieldSet(
                            "detail",
                            Set.of(
                                    "id",
                                    "elderId",
                                    "planId",
                                    "scheduledAt",
                                    "result",
                                    "source",
                                    "recordedAt",
                                    "sourceReference",
                                    "createTime",
                                    "plan",
                                    "elder",
                                    "createBy"))
                    .withFieldSet(
                            "export",
                            Set.of(
                                    "id",
                                    "elderId",
                                    "planId",
                                    "scheduledAt",
                                    "result",
                                    "source",
                                    "recordedAt"));

    public static final CrudResourceDefinition<MedicationRecord> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor(
                            "服药记录", BASE_PATH, "elder:profile-medication-record"),
                    CrudCapabilityDefinition.forTypes(TYPES)
                            .without(
                                    CrudOperation.UPDATE,
                                    CrudOperation.DELETE,
                                    CrudOperation.DELETE_BATCH,
                                    CrudOperation.IMPORT,
                                    CrudOperation.RESTORE,
                                    CrudOperation.ARCHIVE),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.auto(),
                            Set.of("id", "scheduledAt", "recordedAt"),
                            Sort.by("scheduledAt").descending().and(Sort.by("id").descending())),
                    CrudMutationDefinition.forTypes(TYPES),
                    VIEW,
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.none());

    private MedicationRecordResource() {}
}
