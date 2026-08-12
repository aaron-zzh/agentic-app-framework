package com.xuejiai.aaf.module.document.api;

import java.util.Collection;
import java.util.List;

/** 文档引用校验接口，供其他业务模块建立稳定文档引用。 */
public interface DocumentReferenceApi {

    List<OwnedDocument> requireOwned(Collection<Long> documentIds, Long ownerId);

    record OwnedDocument(Long id, Long ownerId) {}
}
