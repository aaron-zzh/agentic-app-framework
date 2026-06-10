package com.xuejiai.aaf.module.ai.role;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.intelligent.assistant.persona.Persona;
import com.xuejiai.aaf.framework.intelligent.assistant.persona.PersonaRepository;

import lombok.RequiredArgsConstructor;

/**
 * Persona CRUD 服务。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonaCrudService
        extends BaseCrudService<Persona, PersonaVO, PersonaCreateDTO, PersonaCreateDTO, PageParam> {

    private final PersonaRepository PersonaRepository;

    @Override
    protected JpaRepository<Persona, Long> getRepository() {
        return PersonaRepository;
    }

    @Override
    protected JpaSpecificationExecutor<Persona> getSpecExecutor() {
        return PersonaRepository;
    }

    @Override
    protected PersonaVO toVO(Persona e) {
        return new PersonaVO(
                e.getId(),
                e.getName(),
                e.getPersona(),
                e.getSystemPrompt(),
                e.getAvatarUrl(),
                e.getStatus(),
                e.getCreateTime(),
                e.getUpdateTime());
    }

    @Override
    protected Persona toEntity(PersonaCreateDTO dto) {
        var entity = new Persona();
        entity.setName(dto.name());
        entity.setPersona(dto.persona());
        entity.setSystemPrompt(dto.systemPrompt());
        entity.setAvatarUrl(dto.avatarUrl());
        return entity;
    }

    @Override
    protected void updateEntity(Persona entity, PersonaCreateDTO dto) {
        entity.setName(dto.name());
        entity.setPersona(dto.persona());
        entity.setSystemPrompt(dto.systemPrompt());
        entity.setAvatarUrl(dto.avatarUrl());
    }

    @Override
    protected String entityName() {
        return "Persona";
    }
}
