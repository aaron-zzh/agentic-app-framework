package com.xuejiai.aaf.module.company.ops.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.company.ops.domain.OpsMetric;
import com.xuejiai.aaf.module.company.ops.domain.OpsTask;
import com.xuejiai.aaf.module.company.ops.domain.OpsTaskExecution;
import com.xuejiai.aaf.module.company.ops.repository.OpsMetricRepository;
import com.xuejiai.aaf.module.company.ops.repository.OpsTaskExecutionRepository;
import com.xuejiai.aaf.module.company.ops.repository.OpsTaskRepository;

import lombok.RequiredArgsConstructor;

/** 运营任务与指标服务 */
@Service
@RequiredArgsConstructor
public class CompanyOpsService {

    private final OpsTaskRepository taskRepository;
    private final OpsTaskExecutionRepository executionRepository;
    private final OpsMetricRepository metricRepository;

    public List<OpsTask> listTasks() {
        return taskRepository.findAll();
    }

    @Transactional
    public OpsTask createTask(OpsTask task) {
        task.setEnabled(true);
        return taskRepository.save(task);
    }

    @Transactional
    public OpsTaskExecution executeTask(Long taskId) {
        taskRepository
                .findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));

        var exec = new OpsTaskExecution();
        exec.setTaskId(taskId);
        exec.setStatus("SUCCESS");
        exec.setStartTime(LocalDateTime.now());
        exec.setEndTime(LocalDateTime.now());
        exec.setTriggeredBy("USER");
        exec.setResult("{\"message\":\"执行完成\"}");
        return executionRepository.save(exec);
    }

    public Page<OpsTaskExecution> getExecutions(Long taskId, Pageable pageable) {
        return executionRepository.findByTaskIdOrderByCreateTimeDesc(taskId, pageable);
    }

    public List<OpsMetric> listMetrics() {
        return metricRepository.findAll();
    }

    @Transactional
    public OpsMetric recordMetric(OpsMetric metric) {
        metric.setRecordedAt(LocalDateTime.now());
        return metricRepository.save(metric);
    }

    public List<OpsMetric> getMetricHistory(String code) {
        return metricRepository.findByCodeOrderByRecordedAtDesc(code);
    }
}
