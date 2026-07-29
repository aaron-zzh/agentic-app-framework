package com.xuejiai.aaf.module.content.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.xuejiai.aaf.module.content.domain.ContentProjectRelation;
import com.xuejiai.aaf.module.content.vo.ContentProjectRelationCreateDTO;

/**
 * 项目关系对象转换器。
 *
 * @author AaronZZH & Kiro
 */
@Mapper
public interface ContentProjectRelationConvert {

    ContentProjectRelationConvert INSTANCE = Mappers.getMapper(ContentProjectRelationConvert.class);

    ContentProjectRelation toEntity(ContentProjectRelationCreateDTO dto);
}
