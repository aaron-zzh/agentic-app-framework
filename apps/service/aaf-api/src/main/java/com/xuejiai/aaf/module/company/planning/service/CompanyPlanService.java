package com.xuejiai.aaf.module.company.planning.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.company.planning.domain.CompanyPlan;
import com.xuejiai.aaf.module.company.planning.repository.CompanyPlanRepository;

import lombok.RequiredArgsConstructor;

/** 企业规划服务 */
@Service
@RequiredArgsConstructor
public class CompanyPlanService {

    private final CompanyPlanRepository planRepository;

    public List<CompanyPlan> listPlans() {
        return planRepository.findAll();
    }

    @Transactional
    public CompanyPlan createPlan(CompanyPlan plan) {
        plan.setStatus("DRAFT");
        return planRepository.save(plan);
    }

    @Transactional
    public CompanyPlan updateStatus(Long id, String status) {
        var plan =
                planRepository
                        .findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("规划不存在: " + id));
        plan.setStatus(status);
        return planRepository.save(plan);
    }
}
