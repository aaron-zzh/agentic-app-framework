package com.xuejiai.aaf.framework.intelligent.action;

import java.util.List;
import java.util.Optional;

/** AI 动作目录策略提供者。framework 定义 SPI，启动模块可用 SQL 实现。 */
public interface AiActionCatalogProvider {

    Optional<AiActionCatalogEntry> find(String entitySlug, String action);

    List<AiActionCatalogEntry> listEnabled();
}
