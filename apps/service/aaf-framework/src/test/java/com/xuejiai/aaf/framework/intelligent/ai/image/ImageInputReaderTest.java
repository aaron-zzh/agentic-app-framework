package com.xuejiai.aaf.framework.intelligent.ai.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ImageInputReaderTest {

    @Test
    void readsBase64ImageDataUrl() throws Exception {
        var input = ImageInputReader.read("data:image/png;base64,AQID");

        assertThat(input.mimeType()).isEqualTo("image/png");
        assertThat(input.bytes()).containsExactly(1, 2, 3);
        assertThat(input.extension()).isEqualTo("png");
    }

    @Test
    void rejectsNonImageDataUrl() {
        assertThatThrownBy(() -> ImageInputReader.read("data:text/plain;base64,AQID"))
                .hasMessageContaining("不是图片类型");
    }

    @Test
    void rejectsUnsupportedUrlScheme() {
        assertThatThrownBy(() -> ImageInputReader.read("file:///secret.png"))
                .hasMessageContaining("协议不受支持");
    }
}
