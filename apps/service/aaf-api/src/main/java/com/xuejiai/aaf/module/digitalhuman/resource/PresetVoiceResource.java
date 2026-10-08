package com.xuejiai.aaf.module.digitalhuman.resource;

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
import com.xuejiai.aaf.module.digitalhuman.controller.PresetVoiceController;
import com.xuejiai.aaf.module.digitalhuman.domain.PresetVoice;

/** 数字人预设音色只读 CRUD 资源定义（全局共享）。 */
public final class PresetVoiceResource {

    public static final ResourceKey KEY = ResourceKey.of("digital-human.preset-voice");
    public static final String BASE_PATH = "/api/preset-voices";

    private static final CrudResourceTypeContract<PresetVoice> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    PresetVoiceController.class, PresetVoice.class);

    public static final CrudResourceDefinition<PresetVoice> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("预设音色", BASE_PATH, "digital-human:preset-voice"),
                    CrudCapabilityDefinition.forTypes(TYPES)
                            .without(
                                    CrudOperation.CREATE,
                                    CrudOperation.UPDATE,
                                    CrudOperation.DELETE,
                                    CrudOperation.DELETE_BATCH,
                                    CrudOperation.IMPORT,
                                    CrudOperation.RESTORE,
                                    CrudOperation.ARCHIVE),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.auto(),
                            Set.of("name", "gender", "provider", "isActive", "displayOrder"),
                            Sort.by("displayOrder").ascending()),
                    CrudMutationDefinition.forTypes(TYPES),
                    CrudViewDefinition.forTypes(TYPES),
                    TenantScope.GLOBAL,
                    PersonalScope.none());

    private PresetVoiceResource() {}
}
