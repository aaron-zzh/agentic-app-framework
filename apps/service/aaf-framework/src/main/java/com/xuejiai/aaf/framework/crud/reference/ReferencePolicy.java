package com.xuejiai.aaf.framework.crud.reference;

import java.util.Set;

/** 默认基线通过后的可选附加引用策略，只能返回输入集合的子集。 */
public interface ReferencePolicy {

    Set<ReferenceContext> filterReadable(Set<ReferenceContext> contexts);

    Set<ReferenceContext> filterReferenceable(Set<ReferenceContext> contexts);
}
