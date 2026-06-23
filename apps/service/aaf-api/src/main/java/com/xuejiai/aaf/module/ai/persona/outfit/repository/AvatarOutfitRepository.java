package com.xuejiai.aaf.module.ai.persona.outfit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.module.ai.persona.outfit.domain.AvatarOutfit;

public interface AvatarOutfitRepository
        extends JpaRepository<AvatarOutfit, Long>, JpaSpecificationExecutor<AvatarOutfit> {}
