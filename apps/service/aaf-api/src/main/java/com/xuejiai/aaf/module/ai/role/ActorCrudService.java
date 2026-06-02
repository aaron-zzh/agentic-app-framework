package com.xuejiai.aaf.module.ai.role;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.intelligent.assistant.actor.Actor;
import com.xuejiai.aaf.framework.intelligent.assistant.actor.ActorRepository;

import lombok.RequiredArgsConstructor;

/**
 * Actor CRUD 服务。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActorCrudService
        extends BaseCrudService<Actor, ActorVO, ActorCreateDTO, ActorCreateDTO, PageParam> {

    private final ActorRepository actorRepository;

    @Override
    protected JpaRepository<Actor, Long> getRepository() {
        return actorRepository;
    }

    @Override
    protected JpaSpecificationExecutor<Actor> getSpecExecutor() {
        return actorRepository;
    }

    @Override
    protected ActorVO toVO(Actor e) {
        return new ActorVO(
                e.getId(),
                e.getActorId(),
                e.getName(),
                e.getPersona(),
                e.getSystemPrompt(),
                e.getAvatarUrl(),
                e.getStatus(),
                e.getCreateTime(),
                e.getUpdateTime());
    }

    @Override
    protected Actor toEntity(ActorCreateDTO dto) {
        var entity = new Actor();
        entity.setActorId(dto.actorId());
        entity.setName(dto.name());
        entity.setPersona(dto.persona());
        entity.setSystemPrompt(dto.systemPrompt());
        entity.setAvatarUrl(dto.avatarUrl());
        return entity;
    }

    @Override
    protected void updateEntity(Actor entity, ActorCreateDTO dto) {
        entity.setName(dto.name());
        entity.setPersona(dto.persona());
        entity.setSystemPrompt(dto.systemPrompt());
        entity.setAvatarUrl(dto.avatarUrl());
    }

    @Override
    protected String entityName() {
        return "Actor";
    }
}
