package com.xuejiai.aaf.module.elder.profile;

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
import com.xuejiai.aaf.module.elder.profile.controller.MedicationPlanController;
import com.xuejiai.aaf.module.elder.profile.domain.MedicationPlan;

/** 用药计划 CRUD 资源定义。 */
public final class MedicationPlanResource {

    public static final ResourceKey KEY = ResourceKey.of("elder.profile.medication-plan");
    public static final String BASE_PATH = "/api/elder/profile/medication-plans";
    public static final String COMMAND_ACTIVATE = "MEDICATION_PLAN_ACTIVATE";
    public static final String COMMAND_DEACTIVATE = "MEDICATION_PLAN_DEACTIVATE";

    private static final CrudResourceTypeContract<MedicationPlan> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    MedicationPlanController.class, MedicationPlan.class);

    private static final CrudViewDefinition VIEW =
            CrudViewDefinition.forTypes(TYPES)
                    .withFieldSet(
                            "list",
                            Set.of(
                                    "id",
                                    "version",
                                    "elderId",
                                    "medicationName",
                                    "dosageInstructions",
                                    "reminderTimes",
                                    "startDate",
                                    "endDate",
                                    "status",
                                    "source",
                                    "nextReminderAt",
                                    "elder"))
                    .withFieldSet("picker", Set.of("id", "medicationName", "status"))
                    .withFieldSet(
                            "detail",
                            Set.of(
                                    "id",
                                    "version",
                                    "elderId",
                                    "medicationName",
                                    "dosageInstructions",
                                    "reminderTimes",
                                    "startDate",
                                    "endDate",
                                    "status",
                                    "source",
                                    "nextReminderAt",
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
                                    "medicationName",
                                    "dosageInstructions",
                                    "reminderTimes",
                                    "startDate",
                                    "endDate",
                                    "status",
                                    "source"));

    public static final CrudResourceDefinition<MedicationPlan> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("用药计划", BASE_PATH, "elder:profile-medication-plan"),
                    CrudCapabilityDefinition.forTypes(TYPES)
                            .without(
                                    CrudOperation.IMPORT,
                                    CrudOperation.RESTORE,
                                    CrudOperation.ARCHIVE),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.auto(),
                            Set.of("id", "medicationName", "startDate", "endDate", "status"),
                            Sort.by("id").descending()),
                    CrudMutationDefinition.forTypes(TYPES)
                            .withCustomUpdateCommands(
                                    Map.of(
                                            COMMAND_ACTIVATE, Set.of("status"),
                                            COMMAND_DEACTIVATE, Set.of("status"))),
                    VIEW,
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.none());

    private MedicationPlanResource() {}
}
