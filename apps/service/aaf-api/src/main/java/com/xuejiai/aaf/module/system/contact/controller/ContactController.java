package com.xuejiai.aaf.module.system.contact.controller;

import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.system.contact.domain.Contact;
import com.xuejiai.aaf.module.system.contact.service.ContactService;
import com.xuejiai.aaf.module.system.contact.vo.ContactDTO;
import com.xuejiai.aaf.module.system.contact.vo.ContactPageParam;
import com.xuejiai.aaf.module.system.contact.vo.ContactVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 联系人管理接口。 */
@Tag(name = "联系人管理")
@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
public class ContactController
        extends BaseCrudController<Contact, ContactVO, ContactDTO, ContactDTO, ContactPageParam> {

    private final ContactService contactService;

    @Override
    protected ContactService getService() {
        return contactService;
    }
}
