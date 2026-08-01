package com.xuejiai.aaf.module.company.okr.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.company.okr.domain.KeyResult;
import com.xuejiai.aaf.module.company.okr.domain.Objective;
import com.xuejiai.aaf.module.company.okr.repository.KeyResultRepository;
import com.xuejiai.aaf.module.company.okr.repository.ObjectiveRepository;
import com.xuejiai.aaf.module.company.okr.vo.KeyResultCreateDTO;
import com.xuejiai.aaf.module.company.okr.vo.ObjectiveCreateDTO;

import lombok.RequiredArgsConstructor;

/** OKR 目标管理服务 */
@Service
@RequiredArgsConstructor
public class CompanyOkrService {

    private final ObjectiveRepository objectiveRepository;
    private final KeyResultRepository keyResultRepository;

    public List<Objective> listObjectives(String period) {
        return period != null
                ? objectiveRepository.findByPeriod(period)
                : objectiveRepository.findAll();
    }

    @Transactional
    public Objective createObjective(ObjectiveCreateDTO request) {
        var objective = new Objective();
        objective.setTitle(request.title());
        objective.setPlanId(request.planId());
        objective.setParentId(request.parentId());
        objective.setOwnerUserId(request.ownerUserId());
        objective.setPeriod(request.period());
        objective.setStatus("NOT_STARTED");
        return objectiveRepository.save(objective);
    }

    public List<KeyResult> listKeyResults(Long objectiveId) {
        return keyResultRepository.findByObjectiveId(objectiveId);
    }

    @Transactional
    public KeyResult createKeyResult(Long objectiveId, KeyResultCreateDTO request) {
        var keyResult = new KeyResult();
        keyResult.setObjectiveId(objectiveId);
        keyResult.setTitle(request.title());
        keyResult.setMetricType(request.metricType());
        keyResult.setStartValue(request.startValue());
        keyResult.setTargetValue(request.targetValue());
        keyResult.setCurrentValue(request.currentValue());
        keyResult.setOwnerUserId(request.ownerUserId());
        keyResult.setStatus("NOT_STARTED");
        return keyResultRepository.save(keyResult);
    }

    @Transactional
    public KeyResult updateProgress(Long krId, java.math.BigDecimal currentValue) {
        var kr =
                keyResultRepository
                        .findById(krId)
                        .orElseThrow(() -> new IllegalArgumentException("KR 不存在: " + krId));
        kr.setCurrentValue(currentValue);
        return keyResultRepository.save(kr);
    }
}
