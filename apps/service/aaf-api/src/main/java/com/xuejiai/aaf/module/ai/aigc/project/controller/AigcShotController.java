package com.xuejiai.aaf.module.ai.aigc.project.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcShot;
import com.xuejiai.aaf.module.ai.aigc.project.service.AigcShotService;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcShotAssetVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcShotCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcShotPageDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcShotUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcShotVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** AIGC 分镜接口。 */
@Tag(name = "AIGC 分镜")
@RestController
@RequestMapping("/api/aigc/shots")
@RequiredArgsConstructor
public class AigcShotController
        extends BaseCrudController<
                AigcShot, AigcShotVO, AigcShotCreateDTO, AigcShotUpdateDTO, AigcShotPageDTO> {

    private final AigcShotService service;

    @Override
    protected BaseCrudService<
                    AigcShot, AigcShotVO, AigcShotCreateDTO, AigcShotUpdateDTO, AigcShotPageDTO>
            getService() {
        return service;
    }

    @Operation(summary = "批量重排镜号（拖拽排序）")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/reorder")
    public Result<Void> reorder(@RequestBody List<Long> orderedIds) {
        service.reorderShots(orderedIds);
        return Result.success();
    }

    @Operation(summary = "查询分镜素材列表")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/assets")
    public Result<List<AigcShotAssetVO>> listAssets(@PathVariable Long id) {
        return Result.success(service.listAssets(id));
    }

    @Operation(summary = "为分镜添加素材")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/assets")
    public Result<AigcShotAssetVO> addAsset(
            @PathVariable Long id,
            @RequestParam Long assetId,
            @RequestParam(defaultValue = "FINAL_VIDEO") String role) {
        return Result.success(service.addAsset(id, assetId, role));
    }

    @Operation(summary = "移除分镜素材")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{id}/assets/{assetId}")
    public Result<Void> removeAsset(@PathVariable Long id, @PathVariable Long assetId) {
        service.removeAsset(id, assetId);
        return Result.success();
    }
}
