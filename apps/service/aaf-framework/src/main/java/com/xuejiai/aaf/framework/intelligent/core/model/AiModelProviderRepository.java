package com.xuejiai.aaf.framework.intelligent.core.model;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** AI 模型供应商数据访问。 */
public interface AiModelProviderRepository extends JpaRepository<AiModelProvider, Long> {

    Optional<AiModelProvider> findByProviderCode(String providerCode);
}
