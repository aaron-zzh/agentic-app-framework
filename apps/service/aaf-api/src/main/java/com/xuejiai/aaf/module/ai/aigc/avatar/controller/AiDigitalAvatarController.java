package com.xuejiai.aaf.module.ai.aigc.avatar.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.intelligent.ai.avatar.AvatarVideoService;
import com.xuejiai.aaf.module.ai.aigc.avatar.domain.AiDigitalAvatar;
import com.xuejiai.aaf.module.ai.aigc.avatar.service.AiDigitalAvatarService;
import com.xuejiai.aaf.module.ai.aigc.avatar.vo.AiDigitalAvatarCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.avatar.vo.AiDigitalAvatarPageDTO;
import com.xuejiai.aaf.module.ai.aigc.avatar.vo.AiDigitalAvatarUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.avatar.vo.AiDigitalAvatarVO;
import com.xuejiai.aaf.module.ai.aigc.avatar.vo.AvatarVideoGenerateDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 数字人形象接口。
 *
 * <p>POST /api/aigc/avatars — 创建形象（自动触发图片检测）
 *
 * <p>GET /api/aigc/avatars — 分页查询
 *
 * <p>PUT /api/aigc/avatars/{id} — 修改名称/默认音色
 *
 * <p>DELETE /api/aigc/avatars/{id} — 逻辑删除
 *
 * <p>POST /api/aigc/avatars/{id}/detect — 重新检测图片
 *
 * <p>POST /api/aigc/avatars/{id}/generate — 提交视频生成任务
 *
 * @author Kiro
 */
@Tag(name = "数字人形象")
@RestController
@RequestMapping("/api/aigc/avatars")
@RequiredArgsConstructor
public class AiDigitalAvatarController
        extends BaseCrudController<
                AiDigitalAvatar,
                AiDigitalAvatarVO,
                AiDigitalAvatarCreateDTO,
                AiDigitalAvatarUpdateDTO,
                AiDigitalAvatarPageDTO> {

    private final AiDigitalAvatarService avatarService;

    @Override
    protected AiDigitalAvatarService getService() {
        return avatarService;
    }

    /** 重新检测图片合规性（检测失败后修正图片 URL 再重试时使用）。 */
    @Operation(summary = "重新检测图片")
    @PostMapping("/{id}/detect")
    public Result<AvatarVideoService.DetectResult> detect(@PathVariable Long id) {
        var avatar = avatarService.getById(id);
        var result = avatarService.reDetect(id);
        return Result.success(result);
    }

    /** 提交数字人视频生成任务，返回 aigc_task.id。 */
    @Operation(summary = "生成数字人视频")
    @PostMapping("/{id}/generate")
    public Result<Long> generate(
            @PathVariable Long id, @Valid @RequestBody AvatarVideoGenerateDTO dto) {
        // TODO: 从安全上下文获取 userId，当前暂用占位符
        Long userId = 0L;
        return Result.success(avatarService.generateVideo(id, dto, userId));
    }
}
