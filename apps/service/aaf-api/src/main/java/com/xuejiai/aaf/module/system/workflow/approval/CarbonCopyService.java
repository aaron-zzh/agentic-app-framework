package com.xuejiai.aaf.module.system.workflow.approval;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.engine.workflow.node.CarbonCopyNode.CarbonCopyEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 抄送服务——记录抄送、查询、标记已读。
 *
 * @author Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CarbonCopyService {

    private final CarbonCopyRepository carbonCopyRepository;

    /** 监听抄送事件，批量记录抄送。 */
    @EventListener
    @Transactional
    public void onCarbonCopy(CarbonCopyEvent event) {
        var users = event.ccUsers().split(",");
        var now = LocalDateTime.now();
        for (String user : users) {
            var trimmed = user.trim();
            if (trimmed.isEmpty()) continue;
            var record = new CarbonCopyRecord();
            record.setProcessInstanceId(event.processInstanceId());
            record.setTaskName(event.taskName());
            record.setCcUser(trimmed);
            record.setCcTime(now);
            record.setEntityType(event.entityType());
            record.setEntityId(event.entityId());
            carbonCopyRepository.save(record);
        }
        log.info(
                "抄送记录已保存: processInstance={}, users={}",
                event.processInstanceId(),
                event.ccUsers());
    }

    /** 查询用户的抄送列表。 */
    @Transactional(readOnly = true)
    public List<CarbonCopyRecord> listByUser(String userId) {
        return carbonCopyRepository.findByCcUserOrderByCcTimeDesc(userId);
    }

    /** 标记抄送已读。 */
    @Transactional
    public void markRead(Long recordId) {
        carbonCopyRepository
                .findById(recordId)
                .ifPresent(
                        record -> {
                            record.setRead(true);
                            carbonCopyRepository.save(record);
                        });
    }

    /** 查询用户未读抄送数。 */
    @Transactional(readOnly = true)
    public long countUnread(String userId) {
        return carbonCopyRepository.countByCcUserAndReadFalse(userId);
    }
}
