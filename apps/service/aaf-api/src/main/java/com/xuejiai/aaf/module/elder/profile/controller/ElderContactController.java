package com.xuejiai.aaf.module.elder.profile.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.elder.profile.domain.ElderContact;
import com.xuejiai.aaf.module.elder.profile.service.ElderContactService;
import com.xuejiai.aaf.module.elder.profile.vo.ElderContactCreateDTO;
import com.xuejiai.aaf.module.elder.profile.vo.ElderContactPageDTO;
import com.xuejiai.aaf.module.elder.profile.vo.ElderContactUpdateDTO;
import com.xuejiai.aaf.module.elder.profile.vo.ElderContactVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 长者联系人接口。 */
@Tag(name = "养老-长者联系人")
@RestController
@RequestMapping("/api/elder/profile/contacts")
@RequiredArgsConstructor
public class ElderContactController
        extends BaseCrudController<
                ElderContact,
                ElderContactVO,
                ElderContactCreateDTO,
                ElderContactUpdateDTO,
                ElderContactPageDTO> {

    private final ElderContactService elderContactService;

    @Override
    protected BaseCrudService<
                    ElderContact,
                    ElderContactVO,
                    ElderContactCreateDTO,
                    ElderContactUpdateDTO,
                    ElderContactPageDTO>
            getService() {
        return elderContactService;
    }
}
