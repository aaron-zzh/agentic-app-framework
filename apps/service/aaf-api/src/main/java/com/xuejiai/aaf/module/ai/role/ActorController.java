/**
 * Actor（人格）管理接口。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.module.ai.role;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.intelligent.assistant.actor.Actor;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "AI 角色管理 - Actor")
@RestController
@RequestMapping("/api/ai/actors")
@RequiredArgsConstructor
public class ActorController
        extends BaseCrudController<Actor, ActorVO, ActorCreateDTO, ActorCreateDTO, PageParam> {

    private final ActorCrudService actorCrudService;

    @Override
    protected ActorCrudService getService() {
        return actorCrudService;
    }
}
