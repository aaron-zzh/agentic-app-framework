package com.xuejiai.aaf.module.ai.aigc.voice.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.ai.aigc.voice.domain.AiClonedVoice;
import com.xuejiai.aaf.module.ai.aigc.voice.service.AiClonedVoiceService;
import com.xuejiai.aaf.module.ai.aigc.voice.vo.AiClonedVoiceCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.voice.vo.AiClonedVoicePageDTO;
import com.xuejiai.aaf.module.ai.aigc.voice.vo.AiClonedVoiceUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.voice.vo.AiClonedVoiceVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 声音复刻接口。
 *
 * <p>POST /api/aigc/cloned-voices — 调百炼平台复刻并持久化
 *
 * <p>GET /api/aigc/cloned-voices — 分页查询本地记录
 *
 * <p>PUT /api/aigc/cloned-voices/{id} — 修改别名/备注
 *
 * <p>DELETE /api/aigc/cloned-voices/{id} — 逻辑删除本地记录（不删除百炼平台音色）
 *
 * @author Kiro
 */
@Tag(name = "声音复刻")
@RestController
@RequestMapping("/api/aigc/cloned-voices")
@RequiredArgsConstructor
public class AiClonedVoiceController
        extends BaseCrudController<
                AiClonedVoice,
                AiClonedVoiceVO,
                AiClonedVoiceCreateDTO,
                AiClonedVoiceUpdateDTO,
                AiClonedVoicePageDTO> {

    private final AiClonedVoiceService voiceService;

    @Override
    protected AiClonedVoiceService getService() {
        return voiceService;
    }
}
