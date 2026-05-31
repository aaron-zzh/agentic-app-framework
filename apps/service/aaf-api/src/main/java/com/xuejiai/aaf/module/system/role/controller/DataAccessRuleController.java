package com.xuejiai.aaf.module.system.role.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.system.role.domain.DataAccessRule;
import com.xuejiai.aaf.module.system.role.service.DataAccessService;
import com.xuejiai.aaf.module.system.role.vo.DataAccessRuleCreateDTO;
import com.xuejiai.aaf.module.system.role.vo.DataAccessRulePageParam;
import com.xuejiai.aaf.module.system.role.vo.DataAccessRuleVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 行级数据权限规则管理接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "数据权限规则管理")
@RestController
@RequestMapping("/api/admin/data-access-rules")
@RequiredArgsConstructor
public class DataAccessRuleController
        extends BaseCrudController<
                DataAccessRule,
                DataAccessRuleVO,
                DataAccessRuleCreateDTO,
                DataAccessRuleCreateDTO,
                DataAccessRulePageParam> {

    private final DataAccessService dataAccessService;

    @Override
    protected DataAccessService getService() {
        return dataAccessService;
    }
}
