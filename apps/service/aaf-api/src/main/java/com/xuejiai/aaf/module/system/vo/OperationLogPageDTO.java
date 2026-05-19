package com.xuejiai.aaf.module.system.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.common.model.PageParam;

/** 操作日志分页查询参数。 */
public class OperationLogPageDTO extends PageParam {

    private String module;
    private String type;
    private Long userId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    public String module() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String type() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long userId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public LocalDateTime startTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime endTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
}
