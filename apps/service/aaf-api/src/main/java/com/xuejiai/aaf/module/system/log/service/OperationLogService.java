package com.xuejiai.aaf.module.system.log.service;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.logging.OperationLogEvent;
import com.xuejiai.aaf.module.system.log.domain.OperationLogEntity;
import com.xuejiai.aaf.module.system.log.repository.OperationLogRepository;
import com.xuejiai.aaf.module.system.log.vo.OperationLogPageDTO;
import com.xuejiai.aaf.module.system.log.vo.OperationLogVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 操作日志业务逻辑。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final OperationLogRepository repository;

    /** 异步监听操作日志事件并持久化。 */
    @Async
    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOperationLogEvent(OperationLogEvent event) {
        var entity = new OperationLogEntity();
        entity.setUserId(event.userId());
        entity.setUsername(event.username());
        entity.setModule(event.module());
        entity.setType(event.type());
        entity.setDescription(event.description());
        entity.setBizNo(event.bizNo());
        entity.setRequestMethod(event.requestMethod());
        entity.setRequestUrl(event.requestUrl());
        entity.setRequestParams(event.requestParams());
        entity.setResponseResult(event.responseResult());
        entity.setIp(event.ip());
        entity.setUserAgent(event.userAgent());
        entity.setDurationMs(event.durationMs());
        entity.setSuccess(event.success());
        entity.setErrorMessage(event.errorMessage());
        entity.setCreateTime(event.createTime());
        repository.save(entity);
    }

    /** 分页查询操作日志。 */
    @Transactional(readOnly = true)
    public PageResult<OperationLogVO> page(OperationLogPageDTO req) {
        var pageable = req.toPageable(Sort.by("id").descending());
        Specification<OperationLogEntity> spec =
                SpecificationBuilder.<OperationLogEntity>builder()
                        .eqIfPresent("module", req.module())
                        .eqIfPresent("type", req.type())
                        .eqIfPresent("userId", req.userId())
                        .betweenIfPresent("createTime", req.startTime(), req.endTime())
                        .build();
        var page = repository.findAll(spec, pageable);
        return new PageResult<>(
                page.getContent().stream().map(this::toVO).toList(), page.getTotalElements());
    }

    private OperationLogVO toVO(OperationLogEntity e) {
        return new OperationLogVO(
                e.getId(),
                e.getUserId(),
                e.getUsername(),
                e.getModule(),
                e.getType(),
                e.getDescription(),
                e.getBizNo(),
                e.getRequestMethod(),
                e.getRequestUrl(),
                e.getDurationMs(),
                e.getSuccess(),
                e.getErrorMessage(),
                e.getCreateTime());
    }
}
