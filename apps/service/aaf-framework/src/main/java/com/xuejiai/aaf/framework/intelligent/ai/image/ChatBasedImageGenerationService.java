package com.xuejiai.aaf.framework.intelligent.ai.image;

import java.util.Base64;
import java.util.List;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.ai.chat.DynamicChatClientFactory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于 chat/completions 接口的文生图实现，支持 Gemini 等多模态输出模型。
 *
 * <p>适用于通过 OpenAI 兼容接口（chat/completions）返回图片 base64 的模型， 如 gemini-3.1-flash-image-preview。结果以
 * b64_json 形式返回。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatBasedImageGenerationService implements ImageGenerationService {

    private final DynamicChatClientFactory chatClientFactory;

    @Override
    public ImageResult generate(ImageRequest request) {
        log.info("[ChatBasedImage] 开始生成: modelId={}", request.modelId());
        var client = chatClientFactory.get(request.modelId());
        var response =
                client.prompt(new Prompt(new UserMessage(request.prompt()))).call().chatResponse();

        // 从响应中提取图片：优先取 media 附件，否则尝试解析 base64 文本
        var result = response.getResult();
        var content = result.getOutput();

        // 检查 media 附件（Spring AI 多模态响应）
        List<Media> mediaList = content.getMedia();
        if (mediaList != null && !mediaList.isEmpty()) {
            var media =
                    mediaList.stream()
                            .filter(m -> m.getMimeType().toString().startsWith("image/"))
                            .findFirst()
                            .orElse(mediaList.get(0));
            String b64 = Base64.getEncoder().encodeToString((byte[]) media.getData());
            log.info("[ChatBasedImage] 生成完成（media）: modelId={}", request.modelId());
            return new ImageResult(null, b64, request.modelId());
        }

        // fallback：文本内容本身就是 base64
        String text = content.getText();
        if (text != null && !text.isBlank()) {
            log.info("[ChatBasedImage] 生成完成（text b64）: modelId={}", request.modelId());
            return new ImageResult(null, text.trim(), request.modelId());
        }

        throw new IllegalStateException("chat/completions 未返回图片内容，modelId=" + request.modelId());
    }

    @Override
    public ImageResult imageToImage(ImageEditRequest request) {
        throw new UnsupportedOperationException("ChatBasedImageGenerationService 暂不支持图生图");
    }

    @Override
    public ImageResult editImage(ImageEditRequest request) {
        throw new UnsupportedOperationException("ChatBasedImageGenerationService 暂不支持局部编辑");
    }
}
