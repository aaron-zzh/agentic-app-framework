package com.xuejiai.aaf.framework.bizlog.diff;

import de.danielbechler.diff.node.DiffNode;

/** 将 java-object-diff 的 DiffNode 转换为可读日志文案。 */
public interface IDiffItemsToLogContentService {

    String toLogContent(DiffNode diffNode, Object source, Object target);
}
