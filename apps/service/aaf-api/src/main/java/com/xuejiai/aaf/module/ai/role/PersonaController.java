/**
 * Persona（人格）管理接口。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.module.ai.role;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.intelligent.assistant.persona.Persona;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "AI 角色管理 - Persona")
@RestController
@RequestMapping("/api/ai/actors")
@RequiredArgsConstructor
public class PersonaController
        extends BaseCrudController<
                Persona, PersonaVO, PersonaCreateDTO, PersonaCreateDTO, PageParam> {

    private final PersonaCrudService PersonaCrudService;

    @Override
    protected PersonaCrudService getService() {
        return PersonaCrudService;
    }
}
