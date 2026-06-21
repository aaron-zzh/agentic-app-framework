package com.xuejiai.aaf.module.system.lead.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.lead.LeadChannelEnum;
import com.xuejiai.aaf.common.enums.lead.LeadStatusEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.common.util.IpUtils;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.system.lead.domain.GuestLead;
import com.xuejiai.aaf.module.system.lead.repository.GuestLeadRepository;
import com.xuejiai.aaf.module.system.lead.vo.GuestLeadCreateDTO;
import com.xuejiai.aaf.module.system.lead.vo.GuestLeadPageDTO;
import com.xuejiai.aaf.module.system.lead.vo.GuestLeadUpdateDTO;
import com.xuejiai.aaf.module.system.lead.vo.GuestLeadVO;

import lombok.RequiredArgsConstructor;

/**
 * 访客线索服务。
 *
 * <p>继承 {@link BaseCrudService} 获得管理端标准 CRUD 能力（分页/查询/创建/更新/删除）。 公开端创建/查询入口由 {@link
 * com.xuejiai.aaf.module.system.lead.controller.PublicLeadController} 调用此处的扩展方法。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class GuestLeadCrudService
        extends BaseCrudService<
                GuestLead, GuestLeadVO, GuestLeadCreateDTO, GuestLeadUpdateDTO, GuestLeadPageDTO> {

    private final GuestLeadRepository repository;

    @Override
    protected JpaRepository<GuestLead, Long> getRepository() {
        return repository;
    }

    @Override
    protected JpaSpecificationExecutor<GuestLead> getSpecExecutor() {
        return repository;
    }

    @Override
    protected GuestLeadVO toVO(GuestLead e) {
        return new GuestLeadVO(
                e.getId(),
                e.getAnonymousId(),
                e.getChannel(),
                e.getEmail(),
                e.getName(),
                e.getPhone(),
                e.getSubject(),
                e.getContent(),
                e.getThreadId(),
                e.getAgentRole(),
                e.getLastMessageAt(),
                e.getIpAddress(),
                e.getUserAgent(),
                e.getReferer(),
                e.getRegion(),
                e.getStatus(),
                e.getHandledBy(),
                e.getHandledTime(),
                e.getContactId(),
                e.getCreateTime(),
                e.getUpdateTime());
    }

    @Override
    protected GuestLead toEntity(GuestLeadCreateDTO dto) {
        validateChannelFields(dto);
        var entity = new GuestLead();
        entity.setAnonymousId(dto.anonymousId());
        entity.setChannel(dto.channel());
        entity.setEmail(dto.email());
        entity.setName(dto.name());
        entity.setPhone(dto.phone());
        entity.setSubject(dto.subject());
        entity.setContent(dto.content());
        entity.setThreadId(dto.threadId());
        entity.setAgentRole(dto.agentRole());
        if (dto.channel() == LeadChannelEnum.CHAT) {
            entity.setLastMessageAt(LocalDateTime.now());
        }
        return entity;
    }

    @Override
    protected void updateEntity(GuestLead entity, GuestLeadUpdateDTO dto) {
        if (dto.status() != null) {
            entity.setStatus(dto.status());
            // 切换到处理中/已处理状态时自动标记处理时间
            if (dto.status() == LeadStatusEnum.RESOLVED
                    || dto.status() == LeadStatusEnum.PROCESSING) {
                entity.setHandledTime(LocalDateTime.now());
            }
        }
        if (dto.handledBy() != null) entity.setHandledBy(dto.handledBy());
        if (dto.contactId() != null) entity.setContactId(dto.contactId());
        if (dto.remark() != null) entity.setRemark(dto.remark());
    }

    @Override
    protected Specification<GuestLead> buildSpec(GuestLeadPageDTO req) {
        return SpecificationBuilder.<GuestLead>builder()
                .eqIfPresent("channel", req.getChannel())
                .eqIfPresent("status", req.getStatus())
                .eqIfPresent("anonymousId", req.getAnonymousId())
                .eqIfPresent("email", req.getEmail())
                .eqIfPresent("contactId", req.getContactId())
                .build();
    }

    @Override
    protected Sort defaultSort() {
        return Sort.by("id").descending();
    }

    @Override
    protected String entityName() {
        return "访客线索";
    }

    @Override
    protected String entitySlug() {
        return "ops_guest_lead";
    }

    // ========== 公开端调用的扩展方法（不走 BaseCrud 鉴权） ==========

    /**
     * 公开端创建线索。
     *
     * <p>除前端可填字段外，由 controller 层注入 IP/UA/Referer，避免被前端伪造； region 通过 {@link IpUtils} 从 IP 推断。
     *
     * <p>NEWSLETTER 渠道做邮箱去重：已订阅则抛 {@link GlobalErrorCode#BAD_REQUEST}（友好提示）； 数据库部分唯一索引作为并发兜底。
     */
    @Transactional
    public GuestLeadVO publicCreate(
            GuestLeadCreateDTO dto, String ipAddress, String userAgent, String referer) {
        validateChannelFields(dto);
        // NEWSLETTER 邮箱预查重——避免无意义的重复订阅记录
        if (dto.channel() == LeadChannelEnum.NEWSLETTER && dto.email() != null) {
            repository
                    .findFirstByChannelAndEmail(LeadChannelEnum.NEWSLETTER, dto.email())
                    .ifPresent(
                            existing -> {
                                throw new BusinessException(
                                        GlobalErrorCode.BAD_REQUEST, "该邮箱已订阅，无需重复操作");
                            });
        }
        var entity = new GuestLead();
        entity.setAnonymousId(dto.anonymousId());
        entity.setChannel(dto.channel());
        entity.setEmail(dto.email());
        entity.setName(dto.name());
        entity.setPhone(dto.phone());
        entity.setSubject(dto.subject());
        entity.setContent(dto.content());
        entity.setThreadId(dto.threadId());
        entity.setAgentRole(dto.agentRole());
        entity.setIpAddress(ipAddress);
        entity.setUserAgent(userAgent);
        entity.setReferer(referer);
        // ip2region 查询失败/不可识别 IP（如 127.0.0.1 / ::1）时返回 null，不影响主流程
        entity.setRegion(ipAddress != null ? safeAreaName(ipAddress) : null);
        entity.setStatus(LeadStatusEnum.NEW);
        if (dto.channel() == LeadChannelEnum.CHAT) {
            entity.setLastMessageAt(LocalDateTime.now());
        }
        return toVO(repository.save(entity));
    }

    /** 调 IpUtils 时静默捕获异常，避免污染 lead 写入主流程。 */
    private static String safeAreaName(String ip) {
        try {
            return IpUtils.getAreaName(ip);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 公开端查询：访客自己的某渠道动作记录（按时间倒序） */
    public List<GuestLeadVO> listByAnonymous(String anonymousId, LeadChannelEnum channel) {
        var list =
                channel != null
                        ? repository.findByAnonymousIdAndChannelOrderByCreateTimeDesc(
                                anonymousId, channel)
                        : repository.findByAnonymousIdOrderByCreateTimeDesc(anonymousId);
        return list.stream().map(this::toVO).toList();
    }

    /** 公开端查询：访客最近一次 CHAT 记录（用于续聊取 threadId） */
    public GuestLeadVO findLatestChat(String anonymousId) {
        return repository
                .findFirstByAnonymousIdAndChannelOrderByLastMessageAtDescCreateTimeDesc(
                        anonymousId, LeadChannelEnum.CHAT)
                .map(this::toVO)
                .orElse(null);
    }

    /** 按 channel 校验必填语义 */
    private void validateChannelFields(GuestLeadCreateDTO dto) {
        switch (dto.channel()) {
            case VISIT -> {
                // 访客访问页面，仅需 anonymousId（DTO 已强制），其它字段全可选
            }
            case CHAT -> {
                // 访客打开对话即创建一条记录（threadId 可在后续消息阶段补全或留空）
                if (isBlank(dto.agentRole())) {
                    throw new BusinessException(
                            GlobalErrorCode.BAD_REQUEST, "CHAT 渠道必须提供 agentRole");
                }
            }
            case NEWSLETTER -> {
                if (isBlank(dto.email())) {
                    throw new BusinessException(
                            GlobalErrorCode.BAD_REQUEST, "NEWSLETTER 渠道必须提供 email");
                }
            }
            case CONTACT, FEEDBACK -> {
                if (isBlank(dto.content())) {
                    throw new BusinessException(
                            GlobalErrorCode.BAD_REQUEST, "CONTACT/FEEDBACK 渠道必须提供 content");
                }
            }
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
