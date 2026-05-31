package com.xuejiai.aaf.framework.engine.tool;

import java.util.List;
import java.util.Optional;

/** 工具目录策略提供者。 */
public interface ToolCatalogProvider {

    Optional<ToolCatalogEntry> find(String toolName);

    List<ToolCatalogEntry> listEnabled();
}
