package com.xuejiai.aaf.module.system.contact.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.common.enums.sys.ContactTypeEnum;
import com.xuejiai.aaf.module.system.contact.domain.Contact;

public interface ContactRepository extends JpaRepository<Contact, Long>, JpaSpecificationExecutor<Contact> {

    List<Contact> findByType(ContactTypeEnum type);

    List<Contact> findByParentId(Long parentId);

    List<Contact> findByNameContaining(String name);

    List<Contact> findByStatus(String status);
}
