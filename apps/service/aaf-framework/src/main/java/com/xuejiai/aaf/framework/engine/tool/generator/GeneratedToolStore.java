package com.xuejiai.aaf.framework.engine.tool.generator;

import java.util.List;
import java.util.Optional;

/** 生成工具持久化契约。 */
public interface GeneratedToolStore {

    void save(GeneratedTool tool);

    Optional<GeneratedTool> findByName(String name);

    List<GeneratedTool> findByCreator(Long userId);

    List<GeneratedTool> findShared();

    /** 查询用户可见的所有工具（自己的 + 共享的）。 */
    List<GeneratedTool> findAccessible(Long userId);

    void updateVisibility(String name, ToolBlueprint.Visibility visibility);
}
