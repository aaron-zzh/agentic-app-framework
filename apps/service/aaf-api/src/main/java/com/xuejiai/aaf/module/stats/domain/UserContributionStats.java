package com.xuejiai.aaf.module.stats.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;

/**
 * 用户贡献量统计只读实体（映射视图 v_user_contribution_stats）。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Entity
@Immutable
@Subselect(
        """
        SELECT s.user_id, s.user_name, s.email, s.register_time, s.last_login_time,
               s.total_aigc_tasks, s.success_aigc_tasks,
               s.image_tasks, s.video_tasks, s.model3d_tasks, s.music_tasks, s.voice_tasks,
               s.credit_balance, s.total_earned_credits, s.total_spent_credits,
               s.total_media_assets, s.total_todos, s.done_todos,
               CASE WHEN u.phone IS NOT NULL
                    THEN CONCAT(LEFT(u.phone, 3), '****', RIGHT(u.phone, 4))
                    ELSE NULL END AS phone
        FROM v_user_contribution_stats s
        JOIN sys_user u ON u.id = s.user_id
        """)
public class UserContributionStats {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "email")
    private String email;

    @Column(name = "register_time")
    private LocalDateTime registerTime;

    @Column(name = "last_login_time")
    private LocalDateTime lastLoginTime;

    @Column(name = "total_aigc_tasks")
    private Long totalAigcTasks;

    @Column(name = "success_aigc_tasks")
    private Long successAigcTasks;

    @Column(name = "image_tasks")
    private Long imageTasks;

    @Column(name = "video_tasks")
    private Long videoTasks;

    @Column(name = "model3d_tasks")
    private Long model3dTasks;

    @Column(name = "music_tasks")
    private Long musicTasks;

    @Column(name = "voice_tasks")
    private Long voiceTasks;

    @Column(name = "credit_balance")
    private Long creditBalance;

    @Column(name = "total_earned_credits")
    private Long totalEarnedCredits;

    @Column(name = "total_spent_credits")
    private Long totalSpentCredits;

    @Column(name = "total_media_assets")
    private Long totalMediaAssets;

    @Column(name = "total_todos")
    private Long totalTodos;

    @Column(name = "done_todos")
    private Long doneTodos;

    /** 脱敏手机号，格式：138****8888 */
    @Column(name = "phone")
    private String phone;
}
