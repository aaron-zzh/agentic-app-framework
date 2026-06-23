package com.xuejiai.aaf.module.tool.meeting;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 会议记录整理接口。
 *
 * <p>积分预检与结算由 {@link com.xuejiai.aaf.framework.engine.credit.AiCreditAspect} 切面 + {@link
 * com.xuejiai.aaf.framework.intelligent.ai.chat.ResilientChatService} 内部统一处理，无需手动扣减。
 */
@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "会议工具")
@RestController
@RequestMapping("/api/tool/meeting")
@RequiredArgsConstructor
public class MeetingOrganizeController {

    private final MeetingOrganizeService meetingOrganizeService;

    @Operation(
            summary = "整理会议记录",
            description = "将 ASR 转写文本整理为结构化会议记录（Markdown），包含议题、决策、待办列表，并补充日期")
    @PostMapping("/organize")
    public Result<MeetingOrganizeVO> organize(@Valid @RequestBody MeetingOrganizeDTO dto) {
        String content = meetingOrganizeService.organize(dto);
        return Result.success(new MeetingOrganizeVO(content));
    }
}
