package com.xuejiai.aaf.module.system.contact.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.module.system.contact.domain.ContactIdentity;

public interface ContactIdentityRepository
        extends JpaRepository<ContactIdentity, Long>, JpaSpecificationExecutor<ContactIdentity> {

    List<ContactIdentity> findByContactId(Long contactId);

    Optional<ContactIdentity> findByChannelAndExternalIdAndCorpId(
            String channel, String externalId, String corpId);

    List<ContactIdentity> findByChannel(String channel);
}
