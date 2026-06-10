/**
 * AI Role 仓储。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.assistant.role;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiRoleRepository
        extends JpaRepository<Role, Long>, JpaSpecificationExecutor<Role> {

    /**
     * 查询助理挂载的角色（经 ai_assistant_role 关联）。
     *
     * <p>角色不再直挂助理，挂载关系统一经 ai_assistant_role 维护。
     */
    @Query(
            "SELECT r FROM AiRole r WHERE r.status = :status AND r.id IN "
                    + "(SELECT ar.roleId FROM AiAssistantRole ar WHERE ar.assistantId = :assistantId)")
    List<Role> findByAssistantIdAndStatus(
            @Param("assistantId") Long assistantId, @Param("status") String status);
}
