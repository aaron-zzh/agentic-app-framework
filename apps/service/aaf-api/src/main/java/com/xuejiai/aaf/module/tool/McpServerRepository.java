package com.xuejiai.aaf.module.tool;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** MCP Server 数据访问层。 */
public interface McpServerRepository extends JpaRepository<McpServer, Long> {

    List<McpServer> findByEnabledTrue();

    boolean existsByName(String name);
}
