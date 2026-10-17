package com.xuejiai.aaf.module.elder.album.api;

import java.time.LocalDateTime;
import java.util.List;

/** 相册跨域只读接口，供养老 Agent 的最近照片工具调用。 */
public interface AlbumQueryApi {

    List<RecentPhoto> listRecentPhotos(Long elderId, int limit);

    record RecentPhoto(
            Long id,
            Long elderId,
            String source,
            LocalDateTime capturedAt,
            String description,
            String thumbnailUrl,
            String originalUrl) {}
}
