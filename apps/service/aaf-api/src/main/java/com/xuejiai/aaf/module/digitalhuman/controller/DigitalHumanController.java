package com.xuejiai.aaf.module.digitalhuman.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;
import com.xuejiai.aaf.module.digitalhuman.dto.ChangeVoiceRequest;
import com.xuejiai.aaf.module.digitalhuman.dto.CreateDigitalHumanRequest;
import com.xuejiai.aaf.module.digitalhuman.dto.LinkAssistantRequest;
import com.xuejiai.aaf.module.digitalhuman.dto.UpdateDigitalHumanRequest;
import com.xuejiai.aaf.module.digitalhuman.service.DigitalHumanService;
import com.xuejiai.aaf.module.digitalhuman.vo.DigitalHumanVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "数字人管理")
@RestController
@RequestMapping("/api/digital-humans")
@RequiredArgsConstructor
public class DigitalHumanController {

    private final DigitalHumanService digitalHumanService;

    @Operation(summary = "创建数字人", description = "系统内部调用，来自 AIGC Blueprint postDeliveryAction")
    @PostMapping
    public Result<DigitalHumanVO> create(@Valid @RequestBody CreateDigitalHumanRequest request) {
        return Result.success(digitalHumanService.create(request));
    }

    @Operation(summary = "分页查询数字人列表")
    @GetMapping
    public Result<Page<DigitalHumanVO>> list(
            @Parameter(description = "名称（模糊查询）") @RequestParam(required = false) String name,
            @Parameter(description = "标签") @RequestParam(required = false) List<String> tags,
            @Parameter(description = "状态") @RequestParam(required = false) String status,
            @PageableDefault(sort = "createTime", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return Result.success(digitalHumanService.list(name, tags, status, pageable));
    }

    @Operation(summary = "获取数字人详情")
    @GetMapping("/{id}")
    public Result<DigitalHumanVO> getById(@PathVariable Long id) {
        return Result.success(digitalHumanService.getById(id));
    }

    @Operation(summary = "更新数字人")
    @PutMapping("/{id}")
    public Result<DigitalHumanVO> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDigitalHumanRequest request
    ) {
        return Result.success(digitalHumanService.update(id, request));
    }

    @Operation(summary = "删除数字人")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        digitalHumanService.delete(id);
        return Result.success();
    }

    @Operation(summary = "切换音色")
    @PostMapping("/{id}/change-voice")
    public Result<DigitalHumanVO> changeVoice(
            @PathVariable Long id,
            @Valid @RequestBody ChangeVoiceRequest request
    ) {
        return Result.success(digitalHumanService.changeVoice(id, request));
    }

    @Operation(summary = "关联助理")
    @PostMapping("/{id}/link-assistant")
    public Result<DigitalHumanVO> linkAssistant(
            @PathVariable Long id,
            @Valid @RequestBody LinkAssistantRequest request
    ) {
        return Result.success(digitalHumanService.linkAssistant(id, request));
    }

    @Operation(summary = "取消关联助理")
    @DeleteMapping("/{id}/link-assistant")
    public Result<DigitalHumanVO> unlinkAssistant(@PathVariable Long id) {
        return Result.success(digitalHumanService.unlinkAssistant(id));
    }

    @Operation(summary = "归档数字人")
    @PostMapping("/{id}/archive")
    public Result<DigitalHumanVO> archive(@PathVariable Long id) {
        return Result.success(digitalHumanService.archive(id));
    }

    @Operation(summary = "恢复数字人")
    @PostMapping("/{id}/restore")
    public Result<DigitalHumanVO> restore(@PathVariable Long id) {
        return Result.success(digitalHumanService.restore(id));
    }

    @Operation(summary = "更新使用统计", description = "视频生成完成后由系统调用")
    @PostMapping("/{id}/usage-stats")
    public Result<Void> updateUsageStats(
            @PathVariable Long id,
            @RequestParam Integer videoCount,
            @RequestParam Integer duration,
            @RequestParam Integer creditsConsumed
    ) {
        digitalHumanService.updateUsageStats(id, videoCount, duration, creditsConsumed);
        return Result.success();
    }
}
