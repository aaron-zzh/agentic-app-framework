package com.xuejiai.aaf.module.chat.livechat.seat.vo;

import com.xuejiai.aaf.common.enums.chat.SeatTypeEnum;
import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 坐席分页查询参数。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "坐席分页查询")
@Data
@EqualsAndHashCode(callSuper = true)
public class SeatPageDTO extends PageParam {

    @Schema(description = "坐席类型")
    private SeatTypeEnum seatType;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "技能组")
    private String skillGroup;
}
