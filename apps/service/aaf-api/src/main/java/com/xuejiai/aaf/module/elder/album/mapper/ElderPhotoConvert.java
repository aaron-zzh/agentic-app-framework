package com.xuejiai.aaf.module.elder.album.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.xuejiai.aaf.module.elder.album.domain.ElderPhoto;
import com.xuejiai.aaf.module.elder.album.vo.ElderPhotoCreateDTO;

/** 长者照片创建对象转换器。 */
@Mapper
public interface ElderPhotoConvert {

    ElderPhotoConvert INSTANCE = Mappers.getMapper(ElderPhotoConvert.class);

    ElderPhoto toEntity(ElderPhotoCreateDTO dto);
}
