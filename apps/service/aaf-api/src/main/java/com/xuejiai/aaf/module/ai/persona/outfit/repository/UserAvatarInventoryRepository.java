package com.xuejiai.aaf.module.ai.persona.outfit.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.persona.outfit.domain.UserAvatarInventory;

public interface UserAvatarInventoryRepository extends JpaRepository<UserAvatarInventory, Long> {

    List<UserAvatarInventory> findByUserIdAndDeletedFalse(Long userId);

    Optional<UserAvatarInventory> findByUserIdAndOutfitIdAndDeletedFalse(
            Long userId, Long outfitId);

    /** 同 personaId 范围内找同 type 的已装备库存（需联表 avatar_outfit，用 JPQL） */
    List<UserAvatarInventory> findByUserIdAndPersonaIdAndEquippedTrueAndDeletedFalse(
            Long userId, Long personaId);

    boolean existsByUserIdAndOutfitIdAndDeletedFalse(Long userId, Long outfitId);
}
