package com.xuejiai.aaf.module.ai.aigc.media.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;
import com.xuejiai.aaf.module.ai.aigc.media.domain.AigcAssetCollection;
import com.xuejiai.aaf.module.ai.aigc.media.service.AigcAssetCollectionService;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetCollectionCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetCollectionItemDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetCollectionItemVO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetCollectionPageDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetCollectionUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetCollectionVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "AIGC 资产集合")
@RestController
@RequestMapping("/api/aigc/asset-collections")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AigcAssetCollectionController
        extends BaseCrudController<
                AigcAssetCollection,
                AigcAssetCollectionVO,
                AigcAssetCollectionCreateDTO,
                AigcAssetCollectionUpdateDTO,
                AigcAssetCollectionPageDTO> {

    private final AigcAssetCollectionService service;

    @Override
    protected AigcAssetCollectionService getService() {
        return service;
    }

    @Operation(summary = "查询集合成员")
    @PreAuthorize("hasAuthority('aigc:asset-collection:read')")
    @GetMapping("/{id}/items")
    public Result<List<AigcAssetCollectionItemVO>> items(@PathVariable Long id) {
        return Result.success(service.items(id));
    }

    @Operation(summary = "添加集合成员")
    @PreAuthorize("hasAuthority('aigc:asset-collection:item')")
    @PostMapping("/{id}/items")
    public Result<AigcAssetCollectionItemVO> addItem(
            @PathVariable Long id, @Valid @RequestBody AigcAssetCollectionItemDTO command) {
        return Result.success(service.addItem(id, command));
    }

    @Operation(summary = "更新集合成员")
    @PreAuthorize("hasAuthority('aigc:asset-collection:item')")
    @PutMapping("/{id}/items/{itemId}")
    public Result<AigcAssetCollectionItemVO> updateItem(
            @PathVariable Long id,
            @PathVariable Long itemId,
            @Valid @RequestBody AigcAssetCollectionItemDTO command) {
        return Result.success(service.updateItem(id, itemId, command));
    }

    @Operation(summary = "移除集合成员")
    @PreAuthorize("hasAuthority('aigc:asset-collection:item')")
    @DeleteMapping("/{id}/items/{itemId}")
    public Result<Void> removeItem(@PathVariable Long id, @PathVariable Long itemId) {
        service.removeItem(id, itemId);
        return Result.success();
    }
}
