package com.xuejiai.aaf.autodev.agent;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Kiro Agent 对话消息实体（autodev_message 表）。 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "autodev_message")
public class AutodevMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属会话 ID */
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** 消息角色：user / assistant / system */
    @Column(name = "role", nullable = false, length = 20)
    private String role = "user";

    /** 消息内容 */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 行动者类型：human（用户输入）/ kiro（kiro-cli 输出）/ ai（AI 回复） */
    @Column(name = "actor_type", nullable = false, length = 20)
    private String actorType = "human";

    /** 该消息关联产生或修改的开发文档 ID */
    @Column(name = "doc_id")
    private Long docId;

    /** 该消息关联的源码文件路径 */
    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime = LocalDateTime.now();

    public static AutodevMessage of(Long sessionId, String role, String actorType, String content) {
        var msg = new AutodevMessage();
        msg.sessionId = sessionId;
        msg.role = role;
        msg.actorType = actorType;
        msg.content = content;
        return msg;
    }
}
