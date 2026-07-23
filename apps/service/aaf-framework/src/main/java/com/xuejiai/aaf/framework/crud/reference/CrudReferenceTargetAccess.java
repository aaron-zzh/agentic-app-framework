package com.xuejiai.aaf.framework.crud.reference;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import com.xuejiai.aaf.framework.crud.definition.ResourceKey;
import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;

/** 非 BaseCrud 资源接入默认引用基线的最小访问契约。 */
public interface CrudReferenceTargetAccess {

    ResourceKey resourceKey();

    Set<Long> readableIds(Collection<Long> ids);

    Set<Long> referenceableIds(Collection<Long> ids);

    Map<Long, ResourceRefDTO> loadReadableRefs(Collection<Long> ids);
}
