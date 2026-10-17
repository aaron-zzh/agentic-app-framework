package com.xuejiai.aaf.module.elder.profile.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.elder.profile.domain.ElderHealthItem;
import com.xuejiai.aaf.module.elder.profile.service.ElderHealthItemService;
import com.xuejiai.aaf.module.elder.profile.vo.ElderHealthItemCreateDTO;
import com.xuejiai.aaf.module.elder.profile.vo.ElderHealthItemPageDTO;
import com.xuejiai.aaf.module.elder.profile.vo.ElderHealthItemUpdateDTO;
import com.xuejiai.aaf.module.elder.profile.vo.ElderHealthItemVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 长者健康条目接口。 */
@Tag(name = "养老-健康条目")
@RestController
@RequestMapping("/api/elder/profile/health-items")
@RequiredArgsConstructor
public class ElderHealthItemController
        extends BaseCrudController<
                ElderHealthItem,
                ElderHealthItemVO,
                ElderHealthItemCreateDTO,
                ElderHealthItemUpdateDTO,
                ElderHealthItemPageDTO> {

    private final ElderHealthItemService service;

    @Override
    protected ElderHealthItemService getService() {
        return service;
    }
}
