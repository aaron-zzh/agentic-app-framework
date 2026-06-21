package com.xuejiai.aaf.module.legal.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.legal.domain.UserConsent;

/**
 * 用户同意快照仓储。
 *
 * @author AaronZZH &amp; Kiro
 */
public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {

    /** 查询用户在某文档类型上的最近一次同意记录。 */
    Optional<UserConsent> findFirstByUserIdAndDocumentTypeOrderByConsentTimeDesc(
            Long userId, String documentType);

    /** 查询用户全部同意记录（合规追溯）。 */
    List<UserConsent> findByUserIdOrderByConsentTimeDesc(Long userId);
}
