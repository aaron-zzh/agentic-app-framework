package com.xuejiai.aaf.module.system.contact.repository;

import java.util.List;

import com.xuejiai.aaf.common.enums.sys.ContactTypeEnum;
import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.system.contact.domain.Contact;

public interface ContactRepository extends CrudEntityRepository<Contact> {

    List<Contact> findByType(ContactTypeEnum type);

    List<Contact> findByParentId(Long parentId);

    List<Contact> findByNameContaining(String name);

    List<Contact> findByStatus(String status);
}
