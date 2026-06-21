package com.xuejiai.aaf.module.tool.weather.vo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 彩云天气 v2.6 综合接口（{@code /weather}）响应 DTO。
 *
 * <p>对照彩云官方文档完整建模，所有字段保持 vendor 命名（snake_case 通过 {@link JsonProperty} 映射）。
 * 用作 vendor adapter 层的反序列化目标——上层（Controller / Tool / Service）可在此基础上裁剪或转换为
 * 业务 VO。
 *
 * <p>字段在不同套餐下可能为 {@code null}（如 minutely / alert 仅企业套餐返回），所有字段统一使用包装类型。
 *
 * <p>主文档：<a href="https://docs.caiyunapp.com/weather-api/v2/v2.6/6-weather.html">综合接口</a>
 */
public record CaiyunWeatherResponse(
        /* 返回状态：ok / failed */
        @JsonProperty("status") String status,
        @JsonProperty("api_version") String apiVersion,
        @JsonProperty("api_status") String apiStatus,
        @JsonProperty("lang") String lang,
        @JsonProperty("unit") String unit,
        @JsonProperty("tzshift") Integer tzshift,
        @JsonProperty("timezone") String timezone,
        @JsonProperty("server_time") Long serverTime,
        /** [纬度, 经度]，与请求经纬度有出入时是网格对齐结果 */
        @JsonProperty("location") List<Double> location,
        @JsonProperty("result") Result result) {

    // ─── result ──────────────────────────────────────────────────────────────

    public record Result(
            @JsonProperty("alert") Alert alert,
            @JsonProperty("realtime") Realtime realtime,
            @JsonProperty("minutely") Minutely minutely,
            @JsonProperty("hourly") Hourly hourly,
            @JsonProperty("daily") Daily daily,
            @JsonProperty("primary") Integer primary,
            /** 短期（未来 2 小时）关键天气变化文字，建议前端突出展示 */
            @JsonProperty("forecast_keypoint") String forecastKeypoint) {}

    // ─── 共享子结构 ──────────────────────────────────────────────────────────

    /** 风：speed 单位由 unit 参数决定（metric=km/h，metric:v2=m/s，imperial=mph） */
    public record Wind(
            @JsonProperty("speed") Double speed,
            @JsonProperty("direction") Double direction) {}

    /** AQI 双数值（chn=国标，usa=美标） */
    public record AqiPair(
            @JsonProperty("chn") Double chn, @JsonProperty("usa") Double usa) {}

    /** AQI 文字描述双语言 */
    public record AqiDescPair(
            @JsonProperty("chn") String chn, @JsonProperty("usa") String usa) {}

    /**
     * 生活指数项。{@code index} 在实况返回 number，在 daily 返回 string，统一用 String 反序列化
     * （Jackson 默认允许 number→string 强转）。
     */
    public record LifeIndexItem(
            @JsonProperty("index") String index, @JsonProperty("desc") String desc) {}

    // ─── alert ───────────────────────────────────────────────────────────────

    public record Alert(
            @JsonProperty("status") String status,
            @JsonProperty("content") List<AlertItem> content,
            @JsonProperty("adcodes") List<Adcode> adcodes) {}

    public record AlertItem(
            @JsonProperty("province") String province,
            @JsonProperty("status") String status,
            /** 预警代码：前两位类型，后两位级别。如 0901=雷电蓝色 */
            @JsonProperty("code") String code,
            @JsonProperty("description") String description,
            @JsonProperty("regionId") String regionId,
            @JsonProperty("county") String county,
            @JsonProperty("pubtimestamp") Long pubtimestamp,
            /** [纬度, 经度] */
            @JsonProperty("latlon") List<Double> latlon,
            @JsonProperty("city") String city,
            @JsonProperty("alertId") String alertId,
            @JsonProperty("title") String title,
            @JsonProperty("adcode") String adcode,
            @JsonProperty("source") String source,
            @JsonProperty("location") String location,
            @JsonProperty("request_status") String requestStatus) {}

    public record Adcode(
            @JsonProperty("adcode") Long adcode, @JsonProperty("name") String name) {}

    // ─── realtime ────────────────────────────────────────────────────────────

    public record Realtime(
            @JsonProperty("status") String status,
            /** 地表 2 米气温 */
            @JsonProperty("temperature") Double temperature,
            /** 体感温度 */
            @JsonProperty("apparent_temperature") Double apparentTemperature,
            /** 地表 2 米相对湿度，0-1 */
            @JsonProperty("humidity") Double humidity,
            /** 总云量，0-1 */
            @JsonProperty("cloudrate") Double cloudrate,
            /** 天气现象，参见 skycon 表 */
            @JsonProperty("skycon") String skycon,
            /** 地表水平能见度 */
            @JsonProperty("visibility") Double visibility,
            /** 向下短波辐射通量，W/m² */
            @JsonProperty("dswrf") Double dswrf,
            /** 地面气压 */
            @JsonProperty("pressure") Double pressure,
            @JsonProperty("wind") Wind wind,
            @JsonProperty("precipitation") RealtimePrecipitation precipitation,
            @JsonProperty("air_quality") RealtimeAirQuality airQuality,
            @JsonProperty("life_index") RealtimeLifeIndex lifeIndex) {}

    public record RealtimePrecipitation(
            @JsonProperty("local") PrecipLocal local,
            @JsonProperty("nearest") PrecipNearest nearest) {}

    public record PrecipLocal(
            @JsonProperty("status") String status,
            @JsonProperty("datasource") String datasource,
            @JsonProperty("intensity") Double intensity) {}

    public record PrecipNearest(
            @JsonProperty("status") String status,
            /** 最近降水带与本地的距离，单位米 */
            @JsonProperty("distance") Double distance,
            @JsonProperty("intensity") Double intensity) {}

    public record RealtimeAirQuality(
            @JsonProperty("pm25") Double pm25,
            @JsonProperty("pm10") Double pm10,
            @JsonProperty("o3") Double o3,
            @JsonProperty("so2") Double so2,
            @JsonProperty("no2") Double no2,
            @JsonProperty("co") Double co,
            @JsonProperty("aqi") AqiPair aqi,
            @JsonProperty("description") AqiDescPair description) {}

    public record RealtimeLifeIndex(
            @JsonProperty("ultraviolet") LifeIndexItem ultraviolet,
            @JsonProperty("comfort") LifeIndexItem comfort) {}

    // ─── minutely ────────────────────────────────────────────────────────────

    public record Minutely(
            @JsonProperty("status") String status,
            @JsonProperty("datasource") String datasource,
            /** 未来 2 小时每分钟的雷达降水强度（120 个） */
            @JsonProperty("precipitation_2h") List<Double> precipitation2h,
            /** 未来 1 小时每分钟的雷达降水强度（60 个） */
            @JsonProperty("precipitation") List<Double> precipitation,
            /** 未来 2 小时每半小时的降水概率（4 个，0-1） */
            @JsonProperty("probability") List<Double> probability,
            @JsonProperty("description") String description) {}

    // ─── hourly ──────────────────────────────────────────────────────────────

    public record Hourly(
            @JsonProperty("status") String status,
            @JsonProperty("description") String description,
            @JsonProperty("precipitation") List<HourlyPrecipitation> precipitation,
            @JsonProperty("temperature") List<HourlyDouble> temperature,
            @JsonProperty("apparent_temperature") List<HourlyDouble> apparentTemperature,
            @JsonProperty("wind") List<HourlyWind> wind,
            @JsonProperty("humidity") List<HourlyDouble> humidity,
            @JsonProperty("cloudrate") List<HourlyDouble> cloudrate,
            @JsonProperty("skycon") List<HourlyString> skycon,
            @JsonProperty("pressure") List<HourlyDouble> pressure,
            @JsonProperty("visibility") List<HourlyDouble> visibility,
            @JsonProperty("dswrf") List<HourlyDouble> dswrf,
            @JsonProperty("air_quality") HourlyAirQuality airQuality) {}

    public record HourlyDouble(
            @JsonProperty("datetime") String datetime, @JsonProperty("value") Double value) {}

    public record HourlyString(
            @JsonProperty("datetime") String datetime, @JsonProperty("value") String value) {}

    public record HourlyPrecipitation(
            @JsonProperty("datetime") String datetime,
            @JsonProperty("value") Double value,
            /** 降水概率，0-100，单位 % */
            @JsonProperty("probability") Double probability) {}

    public record HourlyWind(
            @JsonProperty("datetime") String datetime,
            @JsonProperty("speed") Double speed,
            @JsonProperty("direction") Double direction) {}

    public record HourlyAirQuality(
            @JsonProperty("aqi") List<HourlyAqi> aqi,
            @JsonProperty("pm25") List<HourlyDouble> pm25) {}

    public record HourlyAqi(
            @JsonProperty("datetime") String datetime, @JsonProperty("value") AqiPair value) {}

    // ─── daily ───────────────────────────────────────────────────────────────

    public record Daily(
            @JsonProperty("status") String status,
            @JsonProperty("astro") List<DailyAstro> astro,
            @JsonProperty("precipitation") List<DailyPrecipitation> precipitation,
            /** 白天 08-20 时降水 */
            @JsonProperty("precipitation_08h_20h") List<DailyPrecipitation> precipitation08h20h,
            /** 夜晚 20-次日 08 时降水 */
            @JsonProperty("precipitation_20h_32h") List<DailyPrecipitation> precipitation20h32h,
            @JsonProperty("temperature") List<DailyRange> temperature,
            @JsonProperty("temperature_08h_20h") List<DailyRange> temperature08h20h,
            @JsonProperty("temperature_20h_32h") List<DailyRange> temperature20h32h,
            @JsonProperty("wind") List<DailyWind> wind,
            @JsonProperty("wind_08h_20h") List<DailyWind> wind08h20h,
            @JsonProperty("wind_20h_32h") List<DailyWind> wind20h32h,
            @JsonProperty("humidity") List<DailyRange> humidity,
            @JsonProperty("cloudrate") List<DailyRange> cloudrate,
            @JsonProperty("pressure") List<DailyRange> pressure,
            @JsonProperty("visibility") List<DailyRange> visibility,
            @JsonProperty("dswrf") List<DailyRange> dswrf,
            @JsonProperty("air_quality") DailyAirQuality airQuality,
            @JsonProperty("skycon") List<DailySkycon> skycon,
            @JsonProperty("skycon_08h_20h") List<DailySkycon> skycon08h20h,
            @JsonProperty("skycon_20h_32h") List<DailySkycon> skycon20h32h,
            @JsonProperty("life_index") DailyLifeIndex lifeIndex) {}

    public record DailyAstro(
            @JsonProperty("date") String date,
            @JsonProperty("sunrise") DailyAstroPoint sunrise,
            @JsonProperty("sunset") DailyAstroPoint sunset) {}

    /** astro 时刻为当地时区，tzshift 不作用在此变量 */
    public record DailyAstroPoint(@JsonProperty("time") String time) {}

    public record DailyPrecipitation(
            @JsonProperty("date") String date,
            @JsonProperty("max") Double max,
            @JsonProperty("min") Double min,
            /** 注：当日 avg 为「当前时刻至当天结束的平均值」，非全天平均 */
            @JsonProperty("avg") Double avg,
            @JsonProperty("probability") Double probability) {}

    public record DailyRange(
            @JsonProperty("date") String date,
            @JsonProperty("max") Double max,
            @JsonProperty("min") Double min,
            @JsonProperty("avg") Double avg) {}

    public record DailyWind(
            @JsonProperty("date") String date,
            @JsonProperty("max") Wind max,
            @JsonProperty("min") Wind min,
            @JsonProperty("avg") Wind avg) {}

    public record DailySkycon(
            @JsonProperty("date") String date, @JsonProperty("value") String value) {}

    public record DailyAirQuality(
            @JsonProperty("aqi") List<DailyAqi> aqi,
            @JsonProperty("pm25") List<DailyRange> pm25) {}

    public record DailyAqi(
            @JsonProperty("date") String date,
            @JsonProperty("max") AqiPair max,
            @JsonProperty("min") AqiPair min,
            @JsonProperty("avg") AqiPair avg) {}

    public record DailyLifeIndex(
            @JsonProperty("ultraviolet") List<LifeIndexDailyItem> ultraviolet,
            @JsonProperty("carWashing") List<LifeIndexDailyItem> carWashing,
            @JsonProperty("dressing") List<LifeIndexDailyItem> dressing,
            @JsonProperty("comfort") List<LifeIndexDailyItem> comfort,
            @JsonProperty("coldRisk") List<LifeIndexDailyItem> coldRisk) {}

    public record LifeIndexDailyItem(
            @JsonProperty("date") String date,
            @JsonProperty("index") String index,
            @JsonProperty("desc") String desc) {}
}
