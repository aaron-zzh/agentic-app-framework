package com.xuejiai.aaf.module.ai.aigc.configuration.repository;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcDomainExtension;

/**
 * 行业扩展仓储。
 *
 * @author AaronZZH & Kiro
 */
public interface AigcDomainExtensionRepository extends CrudEntityRepository<AigcDomainExtension> {

    java.util.Optional<AigcDomainExtension> findByIdAndStatusAndDeletedFalse(
            Long id, String status);
}
