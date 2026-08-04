package com.xuejiai.aaf.module.ai.aigc.timeline.resource;

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
import com.xuejiai.aaf.module.ai.aigc.timeline.controller.AigcTimelineController;
import com.xuejiai.aaf.module.ai.aigc.timeline.domain.AigcTimelineComposition;

public final class AigcTimelineResource {

    public static final ResourceKey KEY = ResourceKey.of("aigc.timeline");
    public static final String BASE_PATH = "/api/aigc/timelines";

    private static final CrudResourceTypeContract<AigcTimelineComposition> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    AigcTimelineController.class, AigcTimelineComposition.class);

    public static final CrudResourceDefinition<AigcTimelineComposition> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("AIGC 时间线", BASE_PATH, "aigc:timeline"),
                    CrudCapabilityDefinition.forTypes(TYPES)
                            .without(
                                    CrudOperation.CREATE,
                                    CrudOperation.UPDATE,
                                    CrudOperation.IMPORT,
                                    CrudOperation.RESTORE,
                                    CrudOperation.ARCHIVE),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.auto(),
                            Set.of(
                                    "projectId",
                                    "deliverableObjectId",
                                    "title",
                                    "durationMs",
                                    "fps",
                                    "width",
                                    "height",
                                    "status",
                                    "adoptedRevisionNo"),
                            Sort.by("id").descending()),
                    CrudMutationDefinition.forTypes(TYPES),
                    CrudViewDefinition.forTypes(TYPES),
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.byProperty("ownerId"));

    private AigcTimelineResource() {}
}
