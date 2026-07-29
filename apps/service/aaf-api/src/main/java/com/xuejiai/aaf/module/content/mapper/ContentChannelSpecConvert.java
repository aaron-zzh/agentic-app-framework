package com.xuejiai.aaf.module.content.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.xuejiai.aaf.module.content.domain.ContentChannelSpec;
import com.xuejiai.aaf.module.content.vo.ContentChannelSpecCreateDTO;

/**
 * 渠道规格对象转换器。
 *
 * @author AaronZZH & Kiro
 */
@Mapper
public interface ContentChannelSpecConvert {

    ContentChannelSpecConvert INSTANCE = Mappers.getMapper(ContentChannelSpecConvert.class);

    ContentChannelSpec toEntity(ContentChannelSpecCreateDTO dto);
}
