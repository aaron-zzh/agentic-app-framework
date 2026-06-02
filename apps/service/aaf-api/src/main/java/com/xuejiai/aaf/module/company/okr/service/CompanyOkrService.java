package com.xuejiai.aaf.module.company.okr.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.company.okr.domain.KeyResult;
import com.xuejiai.aaf.module.company.okr.domain.Objective;
import com.xuejiai.aaf.module.company.okr.repository.KeyResultRepository;
import com.xuejiai.aaf.module.company.okr.repository.ObjectiveRepository;

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
    public Objective createObjective(Objective objective) {
        objective.setStatus("NOT_STARTED");
        return objectiveRepository.save(objective);
    }

    public List<KeyResult> listKeyResults(Long objectiveId) {
        return keyResultRepository.findByObjectiveId(objectiveId);
    }

    @Transactional
    public KeyResult createKeyResult(KeyResult kr) {
        kr.setStatus("NOT_STARTED");
        return keyResultRepository.save(kr);
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
