package com.xuejiai.aaf.module.system.contact.controller;

import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.system.contact.domain.ContactIdentity;
import com.xuejiai.aaf.module.system.contact.service.ContactIdentityService;
import com.xuejiai.aaf.module.system.contact.vo.ContactIdentityDTO;
import com.xuejiai.aaf.module.system.contact.vo.ContactIdentityPageParam;
import com.xuejiai.aaf.module.system.contact.vo.ContactIdentityVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 联系人渠道身份管理接口。 */
@Tag(name = "联系人渠道身份管理")
@RestController
@RequestMapping("/api/contact-identities")
@RequiredArgsConstructor
public class ContactIdentityController
        extends BaseCrudController<
                ContactIdentity,
                ContactIdentityVO,
                ContactIdentityDTO,
                ContactIdentityDTO,
                ContactIdentityPageParam> {

    private final ContactIdentityService contactIdentityService;

    @Override
    protected ContactIdentityService getService() {
        return contactIdentityService;
    }

    @Operation(summary = "新增或更新渠道身份（upsert，同步时使用）")
    @PostMapping("/upsert")
    public Result<ContactIdentityVO> upsert(@RequestBody ContactIdentityDTO dto) {
        return Result.success(contactIdentityService.upsert(dto));
    }

    @Operation(summary = "按渠道身份反查联系人（发消息前调用）")
    @GetMapping("/find-contact")
    public Result<Long> findContactId(
            @RequestParam String channel,
            @RequestParam String externalId,
            @RequestParam(required = false) String corpId) {
        return contactIdentityService
                .findContact(channel, externalId, corpId)
                .map(c -> Result.success(c.getId()))
                .orElse(Result.success(null));
    }
}
