package com.xuejiai.aaf.module.elder.profile.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.xuejiai.aaf.framework.crud.definition.Patch;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.JsonNode;

/** 长者档案更新请求；业务字段使用 Patch 区分缺失、null 与设置值。 */
@Schema(description = "更新长者档案")
public record ElderUpdateDTO(
        Patch<Long> userId,
        Patch<String> fullName,
        Patch<String> gender,
        Patch<LocalDate> birthDate,
        Patch<String> idCardNo,
        Patch<String> mobile,
        Patch<String> address,
        Patch<String> livingArrangement,
        Patch<String> preferredName,
        Patch<String> preferences,
        Patch<String> taboos,
        Patch<String> status,
        Patch<Boolean> cameraConsent,
        Patch<Long> cameraConsentBy,
        Patch<LocalDateTime> cameraConsentAt,
        @NotNull @PositiveOrZero @Schema(description = "期望版本号") Integer expectedVersion) {

    public ElderUpdateDTO {
        userId = normalize(userId);
        fullName = normalize(fullName);
        gender = normalize(gender);
        birthDate = normalize(birthDate);
        idCardNo = normalize(idCardNo);
        mobile = normalize(mobile);
        address = normalize(address);
        livingArrangement = normalize(livingArrangement);
        preferredName = normalize(preferredName);
        preferences = normalize(preferences);
        taboos = normalize(taboos);
        status = normalize(status);
        cameraConsent = normalize(cameraConsent);
        cameraConsentBy = normalize(cameraConsentBy);
        cameraConsentAt = normalize(cameraConsentAt);
    }

    @JsonCreator
    public static ElderUpdateDTO fromJson(
            @JsonProperty("userId") JsonNode userId,
            @JsonProperty("fullName") JsonNode fullName,
            @JsonProperty("gender") JsonNode gender,
            @JsonProperty("birthDate") JsonNode birthDate,
            @JsonProperty("idCardNo") JsonNode idCardNo,
            @JsonProperty("mobile") JsonNode mobile,
            @JsonProperty("address") JsonNode address,
            @JsonProperty("livingArrangement") JsonNode livingArrangement,
            @JsonProperty("preferredName") JsonNode preferredName,
            @JsonProperty("preferences") JsonNode preferences,
            @JsonProperty("taboos") JsonNode taboos,
            @JsonProperty("status") JsonNode status,
            @JsonProperty("cameraConsent") JsonNode cameraConsent,
            @JsonProperty("cameraConsentBy") JsonNode cameraConsentBy,
            @JsonProperty("cameraConsentAt") JsonNode cameraConsentAt,
            @JsonProperty("expectedVersion") Integer expectedVersion) {
        return new ElderUpdateDTO(
                Patch.parse(userId, node -> ProfileJsonDecoder.longValue(node, "关联用户 ID")),
                Patch.parse(fullName, node -> ProfileJsonDecoder.text(node, "姓名")),
                Patch.parse(gender, node -> ProfileJsonDecoder.text(node, "性别")),
                Patch.parse(birthDate, node -> ProfileJsonDecoder.date(node, "出生日期")),
                Patch.parse(idCardNo, node -> ProfileJsonDecoder.text(node, "身份证件号")),
                Patch.parse(mobile, node -> ProfileJsonDecoder.text(node, "联系电话")),
                Patch.parse(address, node -> ProfileJsonDecoder.text(node, "居住地址")),
                Patch.parse(livingArrangement, node -> ProfileJsonDecoder.text(node, "居住形态")),
                Patch.parse(preferredName, node -> ProfileJsonDecoder.text(node, "习惯称呼")),
                Patch.parse(preferences, node -> ProfileJsonDecoder.text(node, "生活偏好")),
                Patch.parse(taboos, node -> ProfileJsonDecoder.text(node, "沟通与照护禁忌")),
                Patch.parse(status, node -> ProfileJsonDecoder.text(node, "档案状态")),
                Patch.parse(cameraConsent, node -> ProfileJsonDecoder.bool(node, "摄像头同意")),
                Patch.parse(cameraConsentBy, node -> ProfileJsonDecoder.longValue(node, "同意人 ID")),
                Patch.parse(cameraConsentAt, node -> ProfileJsonDecoder.dateTime(node, "同意时间")),
                expectedVersion);
    }

    private static <T> Patch<T> normalize(Patch<T> patch) {
        return patch == null ? Patch.absent() : patch;
    }
}
