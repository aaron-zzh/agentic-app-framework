package com.xuejiai.aaf.module.system.lead.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.system.lead.domain.GuestLead;
import com.xuejiai.aaf.module.system.lead.service.GuestLeadCrudService;
import com.xuejiai.aaf.module.system.lead.vo.GuestLeadCreateDTO;
import com.xuejiai.aaf.module.system.lead.vo.GuestLeadPageDTO;
import com.xuejiai.aaf.module.system.lead.vo.GuestLeadUpdateDTO;
import com.xuejiai.aaf.module.system.lead.vo.GuestLeadVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 访客线索管理端接口。
 *
 * <p>继承 {@link BaseCrudController} 提供标准 REST CRUD（分页/查询/创建/更新/删除/批量）， 自动启用 {@code @PreAuthorize}
 * 鉴权。访客自助入口在 {@link PublicLeadController}。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "访客线索-管理")
@RestController
@RequestMapping("/api/system/leads")
@RequiredArgsConstructor
public class GuestLeadController
        extends BaseCrudController<
                GuestLead, GuestLeadVO, GuestLeadCreateDTO, GuestLeadUpdateDTO, GuestLeadPageDTO> {

    private final GuestLeadCrudService service;

    @Override
    protected BaseCrudService<
                    GuestLead,
                    GuestLeadVO,
                    GuestLeadCreateDTO,
                    GuestLeadUpdateDTO,
                    GuestLeadPageDTO>
            getService() {
        return service;
    }
}
