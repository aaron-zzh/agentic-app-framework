package com.xuejiai.aaf.module.system.contact.service;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.system.contact.domain.Contact;
import com.xuejiai.aaf.module.system.contact.repository.ContactRepository;
import com.xuejiai.aaf.module.system.contact.vo.ContactDTO;
import com.xuejiai.aaf.module.system.contact.vo.ContactPageParam;
import com.xuejiai.aaf.module.system.contact.vo.ContactVO;

import lombok.RequiredArgsConstructor;

/** 联系人 CRUD 服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContactService
        extends BaseCrudService<Contact, ContactVO, ContactDTO, ContactDTO, ContactPageParam> {

    private final ContactRepository contactRepository;

    @Override
    protected JpaRepository<Contact, Long> getRepository() {
        return contactRepository;
    }

    @Override
    protected JpaSpecificationExecutor<Contact> getSpecExecutor() {
        return contactRepository;
    }

    @Override
    protected ContactVO toVO(Contact e) {
        return new ContactVO(
                e.getId(),
                e.getName(),
                e.getRealName(),
                e.getPhone(),
                e.getEmail(),
                e.getAvatar(),
                e.getType(),
                e.getSource(),
                e.getStatus(),
                e.getParentId(),
                e.getExt(),
                e.getCreateTime(),
                e.getUpdateTime());
    }

    @Override
    protected Contact toEntity(ContactDTO dto) {
        var e = new Contact();
        e.setName(dto.name());
        e.setRealName(dto.realName());
        e.setPhone(dto.phone());
        e.setEmail(dto.email());
        e.setAvatar(dto.avatar());
        if (dto.type() != null) e.setType(dto.type());
        e.setSource(dto.source());
        if (dto.status() != null) e.setStatus(dto.status());
        e.setParentId(dto.parentId());
        e.setExt(dto.ext());
        return e;
    }

    @Override
    protected void updateEntity(Contact e, ContactDTO dto) {
        if (dto.name() != null) e.setName(dto.name());
        if (dto.realName() != null) e.setRealName(dto.realName());
        if (dto.phone() != null) e.setPhone(dto.phone());
        if (dto.email() != null) e.setEmail(dto.email());
        if (dto.avatar() != null) e.setAvatar(dto.avatar());
        if (dto.type() != null) e.setType(dto.type());
        if (dto.source() != null) e.setSource(dto.source());
        if (dto.status() != null) e.setStatus(dto.status());
        if (dto.parentId() != null) e.setParentId(dto.parentId());
        if (dto.ext() != null) e.setExt(dto.ext());
    }

    @Override
    protected Specification<Contact> buildSpec(ContactPageParam p) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (p.getType() != null) predicates.add(cb.equal(root.get("type"), p.getType()));
            if (p.getStatus() != null) predicates.add(cb.equal(root.get("status"), p.getStatus()));
            if (p.getParentId() != null)
                predicates.add(cb.equal(root.get("parentId"), p.getParentId()));
            if (StringUtils.hasText(p.getKeyword()))
                predicates.add(cb.like(root.get("name"), "%" + p.getKeyword() + "%"));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    @Override
    protected String entityName() {
        return "联系人";
    }
}
