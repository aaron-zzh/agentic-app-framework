/**
 * Bundle Search 结果——一组关联记忆原子组成的证据链。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.engine.memory;

import java.util.List;

/**
 * 记忆 Bundle：不是孤立的 Top-K 片段，而是一组通过关系连接的证据链。
 *
 * @param atoms 组成 bundle 的原子
 * @param relations 原子间关系
 * @param score bundle 整体分数
 */
public record MemoryBundle(List<MemoryAtom> atoms, List<MemoryRelation> relations, double score) {}
