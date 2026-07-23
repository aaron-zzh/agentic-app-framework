package com.xuejiai.aaf.module.system.authorization;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthorizationAuditRepository extends JpaRepository<AuthorizationAudit, Long> {}
