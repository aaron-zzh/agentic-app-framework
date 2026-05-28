/**
 * AI Role 仓储。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.assistant.role;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByRoleId(String roleId);
}
