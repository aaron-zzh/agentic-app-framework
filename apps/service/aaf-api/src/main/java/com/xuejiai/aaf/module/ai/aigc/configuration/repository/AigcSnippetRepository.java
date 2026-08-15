package com.xuejiai.aaf.module.ai.aigc.configuration.repository;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcSnippet;

/** 创作片段仓储。 */
public interface AigcSnippetRepository extends CrudEntityRepository<AigcSnippet> {

    boolean existsByOrgIdAndBuiltinCodeAndDeletedFalse(Long orgId, String builtinCode);
}
