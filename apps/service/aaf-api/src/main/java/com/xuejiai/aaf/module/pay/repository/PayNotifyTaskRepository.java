package com.xuejiai.aaf.module.pay.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.pay.domain.PayNotifyTask;

public interface PayNotifyTaskRepository extends JpaRepository<PayNotifyTask, Long> {

    /** 查询待执行的通知任务（PENDING 且 next_notify_time <= now） */
    List<PayNotifyTask> findByStatusAndNextNotifyTimeBefore(String status, LocalDateTime now);
}
