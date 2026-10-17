package com.xuejiai.aaf.module.elder.profile.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.elder.profile.domain.MedicationRecord;
import com.xuejiai.aaf.module.elder.profile.service.MedicationRecordService;
import com.xuejiai.aaf.module.elder.profile.vo.MedicationRecordCreateDTO;
import com.xuejiai.aaf.module.elder.profile.vo.MedicationRecordPageDTO;
import com.xuejiai.aaf.module.elder.profile.vo.MedicationRecordVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 服药记录接口；记录是事实流水，只允许新增和读取。 */
@Tag(name = "养老-服药记录")
@RestController
@RequestMapping("/api/elder/profile/medication-records")
@RequiredArgsConstructor
public class MedicationRecordController
        extends BaseCrudController<
                MedicationRecord,
                MedicationRecordVO,
                MedicationRecordCreateDTO,
                Void,
                MedicationRecordPageDTO> {

    private final MedicationRecordService service;

    @Override
    protected MedicationRecordService getService() {
        return service;
    }
}
