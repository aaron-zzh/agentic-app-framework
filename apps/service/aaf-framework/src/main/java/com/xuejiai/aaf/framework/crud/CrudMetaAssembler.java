package com.xuejiai.aaf.framework.crud;

import com.xuejiai.aaf.framework.crud.definition.CrudResourceDefinition;
import com.xuejiai.aaf.framework.crud.dto.CrudMetaDTO;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceSnapshot;

/** 将已编译资源快照组装为对外 CRUD 元数据。 */
public final class CrudMetaAssembler {

    private CrudMetaAssembler() {}

    public static CrudMetaDTO assemble(
            CrudResourceSnapshot snapshot, CrudResourceDefinition<?> definition) {
        return new CrudMetaDTO(
                snapshot.key().value(),
                snapshot.slug(),
                snapshot.descriptor().label(),
                snapshot.descriptor().clientApiPath(),
                snapshot.descriptor().permissionNamespace(),
                snapshot.fieldSets(),
                snapshot.operations(),
                definition.query().filterSchema().metas(),
                definition.query().sortableFields().stream().sorted().toList(),
                snapshot.tenantScope(),
                snapshot.exposures(),
                snapshot.schemaVersion(),
                snapshot.fingerprint());
    }
}
