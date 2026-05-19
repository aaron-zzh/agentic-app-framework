/**
 * 模型仓库。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.core.model;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** AI 模型数据访问。 */
public interface AiModelRepository extends JpaRepository<AiModel, Long> {

    Optional<AiModel> findByModelId(String modelId);

    List<AiModel> findByEnabledTrueOrderBySortOrder();

    List<AiModel> findByProviderAndEnabledTrue(String provider);
}
