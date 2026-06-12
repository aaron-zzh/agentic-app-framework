package com.xuejiai.aaf.module.pay.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.pay.BizOrderStatusEnum;
import com.xuejiai.aaf.module.pay.domain.PayNotifyTask;
import com.xuejiai.aaf.module.pay.handler.PaySuccessHandler;
import com.xuejiai.aaf.module.pay.repository.PayNotifyTaskRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * 支付成功通知路由服务（持久化可靠通知）。
 *
 * <p>支付成功时先写 pay_notify_task 表，定时任务轮询执行，失败按指数退避重试最多 8 次。
 */
@Slf4j
@Service
public class PayNotifyService {

    private final BizOrderService bizOrderService;
    private final PayNotifyTaskRepository taskRepository;
    private final Map<String, PaySuccessHandler> handlers;

    public PayNotifyService(
            BizOrderService bizOrderService,
            PayNotifyTaskRepository taskRepository,
            List<PaySuccessHandler> handlerList) {
        this.bizOrderService = bizOrderService;
        this.taskRepository = taskRepository;
        this.handlers =
                handlerList.stream()
                        .collect(
                                Collectors.toMap(
                                        PaySuccessHandler::bizOrderType, Function.identity()));
        log.info("[PayNotifyService] 注册 PaySuccessHandler: {}", this.handlers.keySet());
    }

    /** 支付成功时创建通知任务（持久化），事务提交后异步执行 */
    @Transactional
    public void onPaySuccess(Long payOrderId) {
        var bizOrder = bizOrderService.findByPayOrderId(payOrderId);
        if (bizOrder == null) {
            log.warn("[PayNotifyService] 未找到关联业务订单: payOrderId={}", payOrderId);
            return;
        }
        var task = new PayNotifyTask();
        task.setPayOrderId(payOrderId);
        task.setBizOrderType(bizOrder.getOrderType());
        task.setNextNotifyTime(LocalDateTime.now());
        taskRepository.save(task);
        log.info(
                "[PayNotifyService] 通知任务已创建: payOrderId={}, type={}",
                payOrderId,
                bizOrder.getOrderType());

        // 同步立即执行一次（MOCK 渠道场景，减少延迟）
        executeTask(task);
    }

    /** 每 30 秒扫描一次 PENDING 任务，处理失败重试 */
    @Scheduled(fixedDelay = 30_000, initialDelay = 10_000)
    public void retryPendingTasks() {
        var tasks =
                taskRepository.findByStatusAndNextNotifyTimeBefore("PENDING", LocalDateTime.now());
        for (var task : tasks) {
            try {
                executeTask(task);
            } catch (Exception e) {
                log.warn("[PayNotifyService] 重试任务异常: taskId={}", task.getId(), e);
            }
        }
    }

    @Transactional
    public void executeTask(PayNotifyTask task) {
        // 乐观锁防并发重复执行
        task = taskRepository.findById(task.getId()).orElse(null);
        if (task == null || !"PENDING".equals(task.getStatus())) return;

        task.setNotifyTimes(task.getNotifyTimes() + 1);
        task.setLastExecuteTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());

        try {
            // 幂等检查：bizOrder 已 PAID 说明之前已成功处理过
            var bizOrder = bizOrderService.findByPayOrderId(task.getPayOrderId());
            if (bizOrder != null
                    && BizOrderStatusEnum.PAID.getCode().equals(bizOrder.getStatus())) {
                task.setStatus("SUCCESS");
                task.setResponse("幂等跳过（已处理）");
                taskRepository.save(task);
                return;
            }
            var handler = handlers.get(task.getBizOrderType());
            if (handler == null) {
                task.setStatus("FAILURE");
                task.setResponse("无对应 handler: " + task.getBizOrderType());
                log.warn("[PayNotifyService] 未找到 handler: type={}", task.getBizOrderType());
            } else {
                handler.onPaySuccess(task.getPayOrderId());
                task.setStatus("SUCCESS");
                task.setResponse("OK");
                log.info(
                        "[PayNotifyService] 通知成功: taskId={}, type={}",
                        task.getId(),
                        task.getBizOrderType());
            }
        } catch (Exception e) {
            task.setResponse(
                    e.getMessage() != null
                            ? e.getMessage().substring(0, Math.min(200, e.getMessage().length()))
                            : "error");
            if (task.getNotifyTimes() >= task.getMaxNotifyTimes()) {
                task.setStatus("FAILURE");
                log.error(
                        "[PayNotifyService] 通知最终失败: taskId={}, times={}",
                        task.getId(),
                        task.getNotifyTimes(),
                        e);
            } else {
                // 指数退避：1/2/4/8/16/32/64/128 分钟
                long delayMinutes = (long) Math.pow(2, task.getNotifyTimes() - 1);
                task.setNextNotifyTime(LocalDateTime.now().plusMinutes(delayMinutes));
                log.warn(
                        "[PayNotifyService] 通知失败，{}分钟后重试: taskId={}, times={}",
                        delayMinutes,
                        task.getId(),
                        task.getNotifyTimes());
            }
        }
        taskRepository.save(task);
    }
}
