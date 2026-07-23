package com.xuejiai.aaf.module.system.contact.repository;

import java.util.List;
import java.util.Optional;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.system.contact.domain.ContactIdentity;

public interface ContactIdentityRepository extends CrudEntityRepository<ContactIdentity> {

    List<ContactIdentity> findByContactId(Long contactId);

    Optional<ContactIdentity> findByChannelAndExternalIdAndCorpId(
            String channel, String externalId, String corpId);

    List<ContactIdentity> findByChannel(String channel);
}
