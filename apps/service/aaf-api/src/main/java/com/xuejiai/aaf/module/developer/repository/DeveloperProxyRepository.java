package com.xuejiai.aaf.module.developer.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.developer.domain.DeveloperProxy;

public interface DeveloperProxyRepository extends JpaRepository<DeveloperProxy, Long> {

    List<DeveloperProxy> findByParentDeveloperId(Long parentDeveloperId);
}
