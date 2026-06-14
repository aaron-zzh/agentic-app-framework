package com.xuejiai.aaf.module.system.contact.service;

import java.util.Optional;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.system.contact.domain.Contact;
import com.xuejiai.aaf.module.system.contact.domain.ContactIdentity;
import com.xuejiai.aaf.module.system.contact.repository.ContactIdentityRepository;
import com.xuejiai.aaf.module.system.contact.repository.ContactRepository;
import com.xuejiai.aaf.module.system.contact.vo.ContactIdentityDTO;
import com.xuejiai.aaf.module.system.contact.vo.ContactIdentityPageParam;
import com.xuejiai.aaf.module.system.contact.vo.ContactIdentityVO;

import lombok.RequiredArgsConstructor;

/** 联系人渠道身份 CRUD 服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContactIdentityService
        extends BaseCrudService<
                ContactIdentity,
                ContactIdentityVO,
                ContactIdentityDTO,
                ContactIdentityDTO,
                ContactIdentityPageParam> {

    private final ContactIdentityRepository identityRepository;
    private final ContactRepository contactRepository;

    @Override
    protected JpaRepository<ContactIdentity, Long> getRepository() {
        return identityRepository;
    }

    @Override
    protected JpaSpecificationExecutor<ContactIdentity> getSpecExecutor() {
        return identityRepository;
    }

    @Override
    protected ContactIdentityVO toVO(ContactIdentity e) {
        return new ContactIdentityVO(
                e.getId(),
                e.getContactId(),
                e.getChannel(),
                e.getExternalId(),
                e.getCorpId(),
                e.getDisplayName(),
                e.getAvatarUrl(),
                e.getCreateTime(),
                e.getUpdateTime());
    }

    @Override
    protected ContactIdentity toEntity(ContactIdentityDTO dto) {
        var e = new ContactIdentity();
        e.setContactId(dto.contactId());
        e.setChannel(dto.channel());
        e.setExternalId(dto.externalId());
        e.setCorpId(dto.corpId());
        e.setDisplayName(dto.displayName());
        e.setAvatarUrl(dto.avatarUrl());
        return e;
    }

    @Override
    protected void updateEntity(ContactIdentity e, ContactIdentityDTO dto) {
        if (dto.displayName() != null) e.setDisplayName(dto.displayName());
        if (dto.avatarUrl() != null) e.setAvatarUrl(dto.avatarUrl());
    }

    @Override
    protected Specification<ContactIdentity> buildSpec(ContactIdentityPageParam p) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (p.getContactId() != null)
                predicates.add(cb.equal(root.get("contactId"), p.getContactId()));
            if (StringUtils.hasText(p.getChannel()))
                predicates.add(cb.equal(root.get("channel"), p.getChannel()));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    @Override
    protected String entityName() {
        return "渠道身份";
    }

    /**
     * 按渠道身份 upsert（同步时使用）。
     *
     * <p>已存在则更新 displayName/avatarUrl，不存在则新建。
     */
    @Transactional
    public ContactIdentityVO upsert(ContactIdentityDTO dto) {
        return identityRepository
                .findByChannelAndExternalIdAndCorpId(dto.channel(), dto.externalId(), dto.corpId())
                .map(existing -> {
                    existing.setDisplayName(dto.displayName());
                    existing.setAvatarUrl(dto.avatarUrl());
                    return toVO(identityRepository.save(existing));
                })
                .orElseGet(() -> toVO(identityRepository.save(toEntity(dto))));
    }

    /** 按渠道身份反查联系人（发消息前的身份解析入口）。 */
    public Optional<Contact> findContact(String channel, String externalId, String corpId) {
        return identityRepository
                .findByChannelAndExternalIdAndCorpId(channel, externalId, corpId)
                .flatMap(identity -> contactRepository.findById(identity.getContactId()));
    }
}
