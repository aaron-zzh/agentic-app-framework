package com.xuejiai.aaf.module.elder.album.repository;

import java.util.Optional;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.elder.album.domain.ElderPhoto;

/** 长者相册照片数据访问层。 */
public interface ElderPhotoRepository extends CrudEntityRepository<ElderPhoto> {

    Optional<ElderPhoto> findByDeviceIdAndClientPhotoId(Long deviceId, String clientPhotoId);
}
