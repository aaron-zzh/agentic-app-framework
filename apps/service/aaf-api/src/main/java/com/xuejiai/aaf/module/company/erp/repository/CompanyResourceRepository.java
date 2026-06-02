package com.xuejiai.aaf.module.company.erp.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.company.erp.domain.CompanyResource;

public interface CompanyResourceRepository extends JpaRepository<CompanyResource, Long> {

    List<CompanyResource> findByResourceType(String resourceType);

    List<CompanyResource> findByDepartment(String department);
}
