package com.xuejiai.aaf.module.ai.aigc.media.service;

import java.util.List;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.crud.definition.CrudOperation;
import com.xuejiai.aaf.framework.crud.enforcement.AccessMode;
import com.xuejiai.aaf.module.ai.aigc.media.domain.AigcAssetCollection;
import com.xuejiai.aaf.module.ai.aigc.media.domain.AigcAssetCollectionItem;
import com.xuejiai.aaf.module.ai.aigc.media.repository.AigcAssetCollectionItemRepository;
import com.xuejiai.aaf.module.ai.aigc.media.repository.AigcAssetCollectionRepository;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetCollectionCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetCollectionItemDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetCollectionItemVO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetCollectionPageDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetCollectionUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetCollectionVO;

import lombok.RequiredArgsConstructor;

/** 资产集合管理；成员只能通过聚合命令增删改。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AigcAssetCollectionService
        extends BaseCrudService<
                AigcAssetCollection,
                AigcAssetCollectionVO,
                AigcAssetCollectionCreateDTO,
                AigcAssetCollectionUpdateDTO,
                AigcAssetCollectionPageDTO> {

    private final AigcAssetCollectionRepository repository;
    private final AigcAssetCollectionItemRepository itemRepository;
    private final AigcAssetService assetService;

    @Override
    protected AigcAssetCollectionRepository getRepository() {
        return repository;
    }

    @Override
    protected AigcAssetCollectionVO toVO(AigcAssetCollection entity) {
        return new AigcAssetCollectionVO(
                entity.getId(),
                entity.getVersion(),
                entity.getName(),
                entity.getCollectionType(),
                entity.getDescription(),
                entity.getCreateTime(),
                entity.getUpdateTime());
    }

    @Override
    protected AigcAssetCollection toEntity(AigcAssetCollectionCreateDTO request) {
        var entity = new AigcAssetCollection();
        entity.setName(request.name());
        entity.setCollectionType(request.collectionType());
        entity.setDescription(request.description());
        return entity;
    }

    @Override
    protected void updateEntity(AigcAssetCollection entity, AigcAssetCollectionUpdateDTO request) {
        entity.setName(request.name());
        entity.setCollectionType(request.collectionType());
        entity.setDescription(request.description());
    }

    @Override
    protected void beforeDelete(AigcAssetCollection entity) {
        if (itemRepository.existsByCollectionIdAndDeletedFalse(entity.getId())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "集合仍包含资产，请先移除成员");
        }
    }

    @Override
    protected Specification<AigcAssetCollection> buildSpec(AigcAssetCollectionPageDTO request) {
        return SpecificationBuilder.<AigcAssetCollection>builder()
                .likeIfPresent("name", request.getName())
                .eqIfPresent("collectionType", request.getCollectionType())
                .build();
    }

    public List<AigcAssetCollectionItemVO> items(Long collectionId) {
        requireEntity(collectionId, CrudOperation.GET, AccessMode.DEFAULT);
        return itemRepository
                .findByCollectionIdAndDeletedFalseOrderBySortOrderAscIdAsc(collectionId)
                .stream()
                .map(this::toItemVO)
                .toList();
    }

    @PreAuthorize("hasAuthority('aigc:asset-collection:item')")
    @Transactional
    public AigcAssetCollectionItemVO addItem(
            Long collectionId, AigcAssetCollectionItemDTO command) {
        requireEntity(collectionId, CrudOperation.GET, AccessMode.DEFAULT);
        requireAccessibleAsset(command.assetId());
        var item =
                itemRepository
                        .findByCollectionIdAndAssetIdAndDeletedFalse(
                                collectionId, command.assetId())
                        .orElseGet(AigcAssetCollectionItem::new);
        item.setCollectionId(collectionId);
        item.setAssetId(command.assetId());
        item.setRole(command.role());
        item.setSortOrder(command.sortOrder() == null ? 0 : command.sortOrder());
        return toItemVO(itemRepository.save(item));
    }

    @PreAuthorize("hasAuthority('aigc:asset-collection:item')")
    @Transactional
    public AigcAssetCollectionItemVO updateItem(
            Long collectionId, Long itemId, AigcAssetCollectionItemDTO command) {
        requireEntity(collectionId, CrudOperation.GET, AccessMode.DEFAULT);
        var item = requireItem(collectionId, itemId);
        requireAccessibleAsset(command.assetId());
        itemRepository
                .findByCollectionIdAndAssetIdAndDeletedFalse(collectionId, command.assetId())
                .filter(existing -> !existing.getId().equals(itemId))
                .ifPresent(
                        ignored -> {
                            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "资产已在集合中");
                        });
        item.setAssetId(command.assetId());
        item.setRole(command.role());
        item.setSortOrder(command.sortOrder() == null ? 0 : command.sortOrder());
        return toItemVO(itemRepository.save(item));
    }

    @PreAuthorize("hasAuthority('aigc:asset-collection:item')")
    @Transactional
    public void removeItem(Long collectionId, Long itemId) {
        requireEntity(collectionId, CrudOperation.GET, AccessMode.DEFAULT);
        itemRepository.delete(requireItem(collectionId, itemId));
    }

    private AigcAssetCollectionItem requireItem(Long collectionId, Long itemId) {
        return itemRepository
                .findByIdAndCollectionIdAndDeletedFalse(itemId, collectionId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "集合成员不存在"));
    }

    private void requireAccessibleAsset(Long assetId) {
        if (!assetService.isReadable(assetId)) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "资产不存在");
        }
    }

    private AigcAssetCollectionItemVO toItemVO(AigcAssetCollectionItem item) {
        return new AigcAssetCollectionItemVO(
                item.getId(),
                item.getCollectionId(),
                item.getAssetId(),
                item.getRole(),
                item.getSortOrder(),
                item.getCreateTime());
    }
}
