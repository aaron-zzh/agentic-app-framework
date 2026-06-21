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
        SELECT user_id, user_name, email, register_time, last_login_time,
               total_aigc_tasks, success_aigc_tasks,
               image_tasks, video_tasks, model3d_tasks, music_tasks, voice_tasks,
               credit_balance, total_earned_credits, total_spent_credits,
               total_media_assets, total_todos, done_todos
        FROM v_user_contribution_stats
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
}
