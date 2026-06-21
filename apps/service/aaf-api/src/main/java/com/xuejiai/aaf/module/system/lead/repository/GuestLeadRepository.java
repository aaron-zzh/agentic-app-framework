package com.xuejiai.aaf.module.system.lead.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.common.enums.lead.LeadChannelEnum;
import com.xuejiai.aaf.module.system.lead.domain.GuestLead;

/**
 * 访客线索 Repository。
 *
 * @author AaronZZH & Kiro
 */
public interface GuestLeadRepository
        extends JpaRepository<GuestLead, Long>, JpaSpecificationExecutor<GuestLead> {

    /** 按 anonymousId + channel 查询访客的动作记录（按创建时间倒序） */
    List<GuestLead> findByAnonymousIdAndChannelOrderByCreateTimeDesc(
            String anonymousId, LeadChannelEnum channel);

    /** 按 anonymousId 查询访客的所有动作（按创建时间倒序） */
    List<GuestLead> findByAnonymousIdOrderByCreateTimeDesc(String anonymousId);

    /** 取访客最近一次 CHAT 记录用于续聊（含 threadId） */
    Optional<GuestLead> findFirstByAnonymousIdAndChannelOrderByLastMessageAtDescCreateTimeDesc(
            String anonymousId, LeadChannelEnum channel);

    /** NEWSLETTER 渠道按邮箱查重（配合部分唯一索引做友好提示） */
    Optional<GuestLead> findFirstByChannelAndEmail(LeadChannelEnum channel, String email);
}
