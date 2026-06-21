package com.xuejiai.aaf.module.tool;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** MCP Server 配置实体。 */
@Getter
@Setter
@Entity
@Table(name = "ai_mcp_server")
@EntityListeners(AuditingEntityListener.class)
public class McpServer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false, unique = true, length = 128)
    private String name;

    @Column(nullable = false, length = 512)
    private String url;

    @Column(length = 512)
    private String description;

    /** 传输协议：HTTP / SSE / STDIO */
    @Column(nullable = false, length = 16)
    private String transport = "HTTP";

    @Column(nullable = false)
    private Boolean enabled = true;

    /** 连接状态：connected / disconnected / error */
    @Column(nullable = false, length = 16)
    private String status = "disconnected";

    @CreatedDate
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @LastModifiedDate
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
