/**
 * 助理-角色关联仓储。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.assistant.role;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AiAssistantRoleRepository
        extends JpaRepository<AiAssistantRole, Long>, JpaSpecificationExecutor<AiAssistantRole> {

    /** 查询助理挂载的全部角色关联（按排序值升序）。 */
    List<AiAssistantRole> findByAssistantIdOrderBySortOrderAsc(Long assistantId);

    /** 查询某角色被哪些助理挂载。 */
    List<AiAssistantRole> findByRoleId(Long roleId);

    /** 删除助理的全部角色关联（替换式更新时使用）。 */
    void deleteByAssistantId(Long assistantId);
}
