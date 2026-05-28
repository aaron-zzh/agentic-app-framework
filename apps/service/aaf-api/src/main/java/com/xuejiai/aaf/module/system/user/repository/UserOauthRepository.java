package com.xuejiai.aaf.module.system.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.user.domain.UserOauth;

/**
 * OAuth 第三方账号绑定数据访问层。
 *
 * @author AaronZZH & Kiro
 */
public interface UserOauthRepository extends JpaRepository<UserOauth, Long> {

    Optional<UserOauth> findByProviderAndProviderUserId(String provider, String providerUserId);

    List<UserOauth> findByUserId(Long userId);
}
