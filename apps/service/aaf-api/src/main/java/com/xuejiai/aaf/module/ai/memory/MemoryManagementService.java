/**
 * 记忆管理 Service。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.module.ai.memory;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.framework.engine.memory.AtomMemoryEngine;
import com.xuejiai.aaf.framework.engine.memory.MemoryAtom;
import com.xuejiai.aaf.framework.engine.memory.MemoryAtomRepository;
import com.xuejiai.aaf.framework.security.OperatorContext;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MemoryManagementService {

    private final MemoryAtomRepository repository;
    private final AtomMemoryEngine memoryEngine;
    private final OperatorContext operatorContext;

    /**
     * 分页查询当前用户的记忆列表
     *
     * @param scope 范围筛选（可选）
     * @param pageable 分页参数
     * @return 记忆分页结果
     */
    public PageResult<MemoryAtomVO> list(String scope, Pageable pageable) {
        var userId = operatorContext.currentUserId().orElseThrow();
        Page<MemoryAtom> page;
        if (scope != null) {
            page = repository.findByUserIdAndScope(userId, scope, pageable);
        } else {
            page = repository.findByUserId(userId, pageable);
        }
        return new PageResult<>(page.map(this::toVO).toList(), page.getTotalElements());
    }

    /**
     * 搜索记忆（关键词匹配）
     *
     * @param keyword 关键词
     * @param scope 范围筛选（可选）
     * @return 匹配的记忆列表
     */
    public List<MemoryAtomVO> search(String keyword, String scope) {
        var userId = operatorContext.currentUserId().orElseThrow();
        List<MemoryAtom> results;
        if (scope != null) {
            results = repository.findByUserIdAndScopeAndContentContaining(userId, scope, keyword);
        } else {
            results = repository.findByUserIdAndContentContaining(userId, keyword);
        }
        return results.stream().map(this::toVO).toList();
    }

    /**
     * 删除指定记忆
     *
     * @param ids 记忆 ID 列表
     */
    @Transactional
    public void delete(List<UUID> ids) {
        memoryEngine.delete(ids);
    }

    /**
     * 清空当前用户指定范围的记忆
     *
     * @param scope 范围（short_term/long_term/episodic/procedural）
     */
    @Transactional
    public void clearByScope(String scope) {
        var userId = operatorContext.currentUserId().orElseThrow();
        var atoms = repository.findByUserIdAndScope(userId, scope);
        if (!atoms.isEmpty()) {
            memoryEngine.delete(atoms.stream().map(MemoryAtom::getId).toList());
        }
    }

    /**
     * 获取记忆统计信息
     *
     * @return 各范围的记忆数量
     */
    public MemoryStatsVO getStats() {
        var userId = operatorContext.currentUserId().orElseThrow();
        long shortTerm = repository.countByUserIdAndScope(userId, "short_term");
        long longTerm = repository.countByUserIdAndScope(userId, "long_term");
        long episodic = repository.countByUserIdAndScope(userId, "episodic");
        long procedural = repository.countByUserIdAndScope(userId, "procedural");
        return new MemoryStatsVO(shortTerm, longTerm, episodic, procedural, shortTerm + longTerm + episodic + procedural);
    }

    private MemoryAtomVO toVO(MemoryAtom e) {
        return new MemoryAtomVO(e.getId(), e.getUserId(), e.getScope(), e.getContent(),
                e.getEventTime(), e.getWeight(), e.getAccessCount(), e.getLastAccessedAt(),
                e.getTags(), e.getMetadata(), e.getCreatedAt());
    }
}
