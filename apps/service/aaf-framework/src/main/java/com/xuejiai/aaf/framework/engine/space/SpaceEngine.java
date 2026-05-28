package com.xuejiai.aaf.framework.engine.space;

import java.util.List;

/**
 * 空间引擎——虚拟空间管理、多租户隔离、协作空间。
 *
 * <p>职责：工作空间创建/切换、空间内资源隔离、空间成员管理。 v0.2+ 实现。
 */
public interface SpaceEngine {

    /** 创建空间。 */
    String createSpace(SpaceDefinition definition);

    /** 查询用户可访问的空间列表。 */
    List<SpaceInfo> listSpaces(Long userId);

    /** 切换当前空间上下文。 */
    void switchSpace(Long userId, String spaceId);

    /** 空间定义 */
    record SpaceDefinition(String name, String type, Long ownerId) {}

    /** 空间信息 */
    record SpaceInfo(String spaceId, String name, String type, String role) {}
}
