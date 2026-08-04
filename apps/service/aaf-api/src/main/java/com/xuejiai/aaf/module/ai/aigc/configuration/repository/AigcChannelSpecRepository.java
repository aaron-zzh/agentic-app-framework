package com.xuejiai.aaf.module.ai.aigc.configuration.repository;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcChannelSpec;

/**
 * 渠道规格仓储。
 *
 * @author AaronZZH & Kiro
 */
public interface AigcChannelSpecRepository extends CrudEntityRepository<AigcChannelSpec> {

    java.util.List<AigcChannelSpec> findByIdInAndStatusAndDeletedFalse(
            java.util.Collection<Long> ids, String status);

    java.util.List<AigcChannelSpec> findByCodeInAndStatusAndDeletedFalse(
            java.util.Collection<String> codes, String status);
}
