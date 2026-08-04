package com.xuejiai.aaf.module.ai.aigc.media.api;

import com.xuejiai.aaf.module.ai.aigc.media.vo.MediaVO;

/** AIGC 生成子域写入持久媒体的统一接口。 */
public interface MediaApi {

    MediaVO createFromGeneratedFile(GeneratedMediaCommand command);

    MediaVO getByVersionId(Long mediaVersionId, Long userId);
}
