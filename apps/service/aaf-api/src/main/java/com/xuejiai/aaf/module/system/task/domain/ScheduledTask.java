package com.xuejiai.aaf.module.system.task.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 计划任务——统一调度框架的任务定义持久化实体。
 *
 * <h3>调度模式速查</h3>
 *
 * <pre>
 * 模式 A：工作流内部编排（一次调度，步骤有依赖）
 *   trigger_type=CRON, cron=0 0 9 * * ?
 *   action_type=WORKFLOW, action_config={"processKey":"my-flow"}
 *   misfire_policy=RUN_ONCE（重要任务不能漏）
 *
 * 模式 B：步骤独立调度（多条记录，各自 Cron）
 *   建多条 ScheduledTask，各自 processKey 不同
 *
 * 模式 C：工作流内定时等待（BPMN Timer Boundary Event）
 *   同模式 A，步骤间的等待在 BPMN 里配置，不需要多条任务
 *
 * 固定间隔轮询（如图片状态同步）：
 *   trigger_type=FIXED_DELAY, interval_ms=10000, type=image_sync
 * </pre>
 *
 * <h3>action_type 与 action_config 对照</h3>
 *
 * <pre>
 * NOTIFY:   {"userId":1,"title":"标题","content":"内容"}
 * WEBHOOK:  {"url":"https://...","method":"POST","body":"..."}
 * WORKFLOW: {"processKey":"flow-key","variables":{"userId":1}}
 * </pre>
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "sys_scheduled_task")
@SQLDelete(
        sql =
                "UPDATE sys_scheduled_task SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class ScheduledTask extends BaseEntity {

    /** 任务名称 */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 任务类型：trash_cleanup/archive/automation_schedule/user_defined */
    @Column(name = "type", nullable = false, length = 50)
    private String type;

    /** Cron 表达式（trigger_type=CRON 时使用） */
    @Column(name = "cron", length = 50)
    private String cron;

    /** 触发类型：CRON（默认）/ FIXED_DELAY / FIXED_RATE */
    @Column(name = "trigger_type", nullable = false, length = 20)
    private String triggerType = "CRON";

    /** 间隔毫秒数（trigger_type=FIXED_DELAY/FIXED_RATE 时使用） */
    @Column(name = "interval_ms")
    private Long intervalMs;

    /** 动作类型（用户自定义任务使用）： NOTIFY — 发送通知 WORKFLOW — 触发 AI 工作流 WEBHOOK — 调用外部 URL */
    @Column(name = "action_type", length = 30)
    private String actionType;

    /** 动作配置 JSON（与 actionType 对应的参数，如通知内容、工作流 ID、Webhook URL） */
    @Column(name = "action_config", columnDefinition = "TEXT")
    private String actionConfig;

    /** 错过执行补偿策略：IGNORE / RUN_ONCE */
    @Column(name = "misfire_policy", length = 20)
    private String misfirePolicy = "IGNORE";

    /** 节假日日历编码，执行前判断当天是否排除（null=不排除） */
    @Column(name = "calendar_code", length = 50)
    private String calendarCode;

    /** 状态：active/paused/failed */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "active";

    /** 上次执行时间 */
    @Column(name = "last_run")
    private LocalDateTime lastRun;

    /** 下次执行时间 */
    @Column(name = "next_run")
    private LocalDateTime nextRun;

    /** 连续失败次数 */
    @Column(name = "fail_count", nullable = false)
    private Integer failCount = 0;
}
