package com.xuejiai.aaf.module.system.file.resource;

import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Sort;

import com.xuejiai.aaf.framework.crud.definition.CrudCapabilityDefinition;
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
import com.xuejiai.aaf.module.system.file.FileConfigController;
import com.xuejiai.aaf.module.system.file.domain.FileConfig;

/** 文件存储配置的唯一代码资源定义。 */
public final class FileConfigResource {

    public static final ResourceKey KEY = ResourceKey.of("system.file-config");
    public static final String BASE_PATH = "/api/system/file-configs";

    private static final CrudResourceTypeContract<FileConfig> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    FileConfigController.class, FileConfig.class);

    public static final CrudResourceDefinition<FileConfig> DEFINITION =
            CrudResourceDefinition.standard(
                            KEY,
                            TYPES,
                            new CrudResourceDescriptor("文件存储配置", BASE_PATH, "system:file-config"),
                            CrudCapabilityDefinition.forTypes(TYPES)
                                    .without(
                                            CrudOperation.IMPORT,
                                            CrudOperation.VALIDATE,
                                            CrudOperation.RESTORE,
                                            CrudOperation.ARCHIVE,
                                            CrudOperation.DELETE_BATCH),
                            new CrudQueryDefinition<>(
                                    CrudFilterSchema.auto(),
                                    Set.of("name", "storageType", "master", "status"),
                                    Sort.by("id").descending()),
                            com.xuejiai.aaf.framework.crud.definition.CrudMutationDefinition
                                    .forTypes(TYPES),
                            CrudViewDefinition.forTypes(TYPES),
                            TenantScope.GLOBAL,
                            PersonalScope.none())
                    .withCustomUpdateCommands(
                            Map.of(
                                    "SET_MASTER", Set.of("master"),
                                    "RETIRE", Set.of("status"),
                                    "TEST", Set.of("config")));

    private FileConfigResource() {}
}
