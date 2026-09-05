package com.xuejiai.aaf.module.ai.aigc.media.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaType;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcUploadedMediaCommand;
import com.xuejiai.aaf.module.ai.aigc.media.service.AigcAssetService;
import com.xuejiai.aaf.module.ai.aigc.media.service.AigcMediaService;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcUploadedImageMaterializeDTO;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class AigcMediaControllerTest extends BaseMockitoUnitTest {

    @Mock private AigcMediaService mediaService;
    @Mock private AigcAssetService assetService;
    @Mock private OperatorContext operatorContext;

    @Test
    @DisplayName("Given 当前用户上传图片 When 物化 fileId Then 使用既有上传媒体能力且不复制文件")
    void should_materialize_current_user_uploaded_image() {
        // 准备参数
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(7L));
        var controller = new AigcMediaController(mediaService, assetService, operatorContext);
        var request = new AigcUploadedImageMaterializeDTO(88L, "参考图", 99L);

        // 调用
        var result = controller.materializeUploadedImage(request);

        // 断言
        var captor = ArgumentCaptor.forClass(AigcUploadedMediaCommand.class);
        verify(mediaService).createFromUploadedFile(captor.capture());
        assertThat(result.isSuccess()).isTrue();
        assertThat(captor.getValue())
                .isEqualTo(new AigcUploadedMediaCommand(7L, "参考图", AigcMediaType.IMAGE, 88L, 99L));
    }
}
