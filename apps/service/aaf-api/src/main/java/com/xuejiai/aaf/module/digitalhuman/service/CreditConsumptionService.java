package com.xuejiai.aaf.module.digitalhuman.service;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.ReadonlyCrudService;
import com.xuejiai.aaf.module.digitalhuman.domain.CreditConsumption;
import com.xuejiai.aaf.module.digitalhuman.mapper.CreditConsumptionConvert;
import com.xuejiai.aaf.module.digitalhuman.repository.CreditConsumptionRepository;
import com.xuejiai.aaf.module.digitalhuman.vo.CreditConsumptionPageDTO;
import com.xuejiai.aaf.module.digitalhuman.vo.CreditConsumptionVO;

import lombok.RequiredArgsConstructor;

/** 数字人积分消耗记录只读服务；记录由积分扣减流程写入。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreditConsumptionService
        extends ReadonlyCrudService<
                CreditConsumption, CreditConsumptionVO, CreditConsumptionPageDTO> {

    private final CreditConsumptionRepository repository;

    @Override
    protected CreditConsumptionRepository getRepository() {
        return repository;
    }

    @Override
    protected CreditConsumptionVO toVO(CreditConsumption entity) {
        return CreditConsumptionConvert.INSTANCE.toVO(entity);
    }

    @Override
    protected Specification<CreditConsumption> buildSpec(CreditConsumptionPageDTO request) {
        return SpecificationBuilder.<CreditConsumption>builder()
                .eqIfPresent("digitalHumanId", request.getDigitalHumanId())
                .eqIfPresent("projectId", request.getProjectId())
                .eqIfPresent("operation", request.getOperation())
                .build();
    }
}
