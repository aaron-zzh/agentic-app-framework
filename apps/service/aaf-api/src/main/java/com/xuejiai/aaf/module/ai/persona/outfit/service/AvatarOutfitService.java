package com.xuejiai.aaf.module.ai.persona.outfit.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.crud.ReadonlyCrudService;
import com.xuejiai.aaf.framework.engine.credit.CreditService;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.persona.outfit.domain.AvatarOutfit;
import com.xuejiai.aaf.module.ai.persona.outfit.domain.UserAvatarInventory;
import com.xuejiai.aaf.module.ai.persona.outfit.repository.AvatarOutfitRepository;
import com.xuejiai.aaf.module.ai.persona.outfit.repository.UserAvatarInventoryRepository;
import com.xuejiai.aaf.module.ai.persona.outfit.vo.AvatarOutfitPageDTO;
import com.xuejiai.aaf.module.ai.persona.outfit.vo.AvatarOutfitVO;
import com.xuejiai.aaf.module.ai.persona.outfit.vo.EquipDTO;
import com.xuejiai.aaf.module.ai.persona.outfit.vo.UserAvatarInventoryVO;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

/** 装扮商城服务（查询、购买）。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AvatarOutfitService
        extends ReadonlyCrudService<AvatarOutfit, AvatarOutfitVO, AvatarOutfitPageDTO> {

    private final AvatarOutfitRepository outfitRepository;
    private final UserAvatarInventoryRepository inventoryRepository;
    private final CreditService creditService;
    private final OperatorContext operatorContext;

    @Override
    protected JpaRepository<AvatarOutfit, Long> getRepository() {
        return outfitRepository;
    }

    @Override
    protected JpaSpecificationExecutor<AvatarOutfit> getSpecExecutor() {
        return outfitRepository;
    }

    @Override
    protected String entityName() {
        return "装扮";
    }

    @Override
    protected AvatarOutfitVO toVO(AvatarOutfit e) {
        var vo = new AvatarOutfitVO();
        vo.setId(e.getId());
        vo.setCode(e.getCode());
        vo.setName(e.getName());
        vo.setType(e.getType());
        vo.setAssetUrl(e.getAssetUrl());
        vo.setThumbnailUrl(e.getThumbnailUrl());
        vo.setRarity(e.getRarity());
        vo.setUnlockCondition(e.getUnlockCondition());
        vo.setEntitlementCode(e.getEntitlementCode());
        vo.setPrice(e.getPrice());
        vo.setSortOrder(e.getSortOrder());
        vo.setCreateTime(e.getCreateTime());
        return vo;
    }

    @Override
    protected Specification<AvatarOutfit> buildSpec(AvatarOutfitPageDTO p) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (p.getType() != null) predicates.add(cb.equal(root.get("type"), p.getType()));
            return predicates.isEmpty() ? null : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 分页查询装扮：覆写父类 page() 以批量回填当前用户的 owned/equipped 字段。
     *
     * <p>N9: 商城去重已拥有装扮——前端按 owned=true 置灰"购买"按钮显示"已拥有"。
     */
    @Override
    public com.xuejiai.aaf.common.model.PageResult<AvatarOutfitVO> page(
            AvatarOutfitPageDTO request) {
        var base = super.page(request);
        enrichOwnedAndEquipped(base.list());
        return base;
    }

    /** 批量回填当前用户的 owned/equipped 字段（无登录态全部 false）。 */
    private void enrichOwnedAndEquipped(List<AvatarOutfitVO> vos) {
        if (vos == null || vos.isEmpty()) return;
        Long userId = operatorContext.currentUserId().orElse(null);
        if (userId == null) {
            vos.forEach(
                    vo -> {
                        vo.setOwned(false);
                        vo.setEquipped(false);
                    });
            return;
        }
        var invList = inventoryRepository.findByUserIdAndDeletedFalse(userId);
        var ownedIds =
                invList.stream().map(UserAvatarInventory::getOutfitId).collect(Collectors.toSet());
        var equippedIds =
                invList.stream()
                        .filter(inv -> Boolean.TRUE.equals(inv.getEquipped()))
                        .map(UserAvatarInventory::getOutfitId)
                        .collect(Collectors.toSet());
        vos.forEach(
                vo -> {
                    vo.setOwned(ownedIds.contains(vo.getId()));
                    vo.setEquipped(equippedIds.contains(vo.getId()));
                });
    }

    /** 购买装扮：扣积分 → 写库存。 */
    @Transactional
    public UserAvatarInventoryVO purchase(Long outfitId) {
        Long userId = operatorContext.currentUserId().orElseThrow();
        var outfit =
                outfitRepository
                        .findById(outfitId)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "装扮不存在"));

        if (inventoryRepository.existsByUserIdAndOutfitIdAndDeletedFalse(userId, outfitId)) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "已拥有该装扮");
        }
        if (outfit.getPrice() == null || outfit.getPrice() <= 0) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "该装扮不支持购买");
        }

        // 扣积分
        creditService.spend(
                userId,
                outfit.getPrice(),
                "OUTFIT_PURCHASE",
                "OUTFIT",
                String.valueOf(outfitId),
                0L,
                "购买装扮：" + outfit.getName(),
                "OUTFIT_PURCHASE");

        // 写库存
        var inv = new UserAvatarInventory();
        inv.setUserId(userId);
        inv.setOutfitId(outfitId);
        inv.setObtainedAt(LocalDateTime.now());
        inv.setObtainedSource("PURCHASE");
        inv.setEquipped(false);
        inventoryRepository.save(inv);

        return toInventoryVO(inv, outfit);
    }

    /** 我的库存（联表回填装扮信息）。 */
    public List<UserAvatarInventoryVO> myInventory() {
        Long userId = operatorContext.currentUserId().orElseThrow();
        var invList = inventoryRepository.findByUserIdAndDeletedFalse(userId);
        if (invList.isEmpty()) return List.of();

        var outfitIds = invList.stream().map(UserAvatarInventory::getOutfitId).toList();
        Map<Long, AvatarOutfit> outfitMap =
                outfitRepository.findAllById(outfitIds).stream()
                        .collect(Collectors.toMap(AvatarOutfit::getId, o -> o));

        return invList.stream()
                .map(inv -> toInventoryVO(inv, outfitMap.get(inv.getOutfitId())))
                .toList();
    }

    /** 装备：先卸下同类型已装备 → 装备指定 outfit。 */
    @Transactional
    public UserAvatarInventoryVO equip(EquipDTO dto) {
        Long userId = operatorContext.currentUserId().orElseThrow();
        var inv =
                inventoryRepository
                        .findByUserIdAndOutfitIdAndDeletedFalse(userId, dto.outfitId())
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "库存不存在"));

        var outfit =
                outfitRepository
                        .findById(dto.outfitId())
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "装扮不存在"));

        // 卸下同 personaId 范围内同 type 的已装备
        var equipped =
                inventoryRepository.findByUserIdAndPersonaIdAndEquippedTrueAndDeletedFalse(
                        userId, dto.personaId());
        equipped.stream()
                .filter(
                        e -> {
                            var o = outfitRepository.findById(e.getOutfitId()).orElse(null);
                            return o != null && o.getType().equals(outfit.getType());
                        })
                .forEach(
                        e -> {
                            e.setEquipped(false);
                            inventoryRepository.save(e);
                        });

        inv.setEquipped(true);
        inv.setPersonaId(dto.personaId());
        inventoryRepository.save(inv);

        return toInventoryVO(inv, outfit);
    }

    /** 卸下装备。 */
    @Transactional
    public void unequip(EquipDTO dto) {
        Long userId = operatorContext.currentUserId().orElseThrow();
        inventoryRepository
                .findByUserIdAndOutfitIdAndDeletedFalse(userId, dto.outfitId())
                .ifPresent(
                        inv -> {
                            inv.setEquipped(false);
                            inventoryRepository.save(inv);
                        });
    }

    private UserAvatarInventoryVO toInventoryVO(UserAvatarInventory inv, AvatarOutfit outfit) {
        var vo = new UserAvatarInventoryVO();
        vo.setId(inv.getId());
        vo.setOutfitId(inv.getOutfitId());
        vo.setPersonaId(inv.getPersonaId());
        vo.setObtainedAt(inv.getObtainedAt());
        vo.setObtainedSource(inv.getObtainedSource());
        vo.setEquipped(inv.getEquipped());
        if (outfit != null) vo.setOutfit(toVO(outfit));
        return vo;
    }
}
