package com.xuejiai.aaf.module.elder.profile.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.xuejiai.aaf.common.enums.elder.ElderLivingArrangementEnum;
import com.xuejiai.aaf.common.enums.elder.ElderStatusEnum;
import com.xuejiai.aaf.common.validation.InEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 长者档案创建请求。 */
@Schema(description = "创建长者档案")
public record ElderCreateDTO(
        @Positive @Schema(description = "可选关联的 AAF 用户 ID") Long userId,
        @NotBlank @Size(max = 100) @Schema(description = "姓名") String fullName,
        @Schema(description = "性别字典值：0/1/2") String gender,
        @PastOrPresent @Schema(description = "出生日期") LocalDate birthDate,
        @Size(max = 64) @Schema(description = "身份证件号") String idCardNo,
        @Size(max = 32) @Schema(description = "联系电话") String mobile,
        @Size(max = 500) @Schema(description = "居住地址") String address,
        @InEnum(value = ElderLivingArrangementEnum.class, message = "居住形态必须是 {value}")
                String livingArrangement,
        @Size(max = 100) @Schema(description = "习惯称呼") String preferredName,
        @Schema(description = "生活偏好") String preferences,
        @Schema(description = "沟通与照护禁忌") String taboos,
        @InEnum(value = ElderStatusEnum.class, message = "档案状态必须是 {value}") String status,
        @Schema(description = "是否已同意摄像头与预警功能") Boolean cameraConsent,
        @Positive @Schema(description = "同意人用户 ID") Long cameraConsentBy,
        @Schema(description = "同意时间") LocalDateTime cameraConsentAt) {}
