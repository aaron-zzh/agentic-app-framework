package com.xuejiai.aaf.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.converters.longconverter.LongStringConverter;
import org.apache.fesod.sheet.support.ExcelTypeEnum;
import org.apache.fesod.sheet.write.metadata.style.WriteCellStyle;
import org.apache.fesod.sheet.write.metadata.style.WriteFont;
import org.apache.fesod.sheet.write.style.HorizontalCellStyleStrategy;
import org.apache.fesod.sheet.write.style.column.LongestMatchColumnWidthStyleStrategy;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

/**
 * Excel 导出工具类。基于 Apache Fesod 封装。
 *
 * <p>功能：自动列宽 + 表头灰底居中 + 内容左对齐 + Long 精度保护 + 异常安全。
 *
 * <p>使用示例：
 * <pre>{@code
 * ExcelUtils.write(response, "用户列表", "用户", UserVO.class, dataList);
 * ExcelUtils.write(response, "用户列表", "用户", UserVO.class, dataList, ExcelTypeEnum.CSV);
 * }</pre>
 */
@Slf4j
public final class ExcelUtils {

    private ExcelUtils() {}

    /** 导出 XLSX。 */
    public static <T> void write(HttpServletResponse response, String filename,
                                 String sheetName, Class<T> head, List<T> data) throws IOException {
        write(response, filename, sheetName, head, data, ExcelTypeEnum.XLSX);
    }

    /** 导出 Excel/CSV，支持格式切换。 */
    public static <T> void write(HttpServletResponse response, String filename,
                                 String sheetName, Class<T> head, List<T> data,
                                 ExcelTypeEnum type) throws IOException {
        setResponseHeaders(response, filename, type);
        try {
            FesodSheet.write(response.getOutputStream(), head)
                    .excelType(type)
                    .autoCloseStream(false)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .registerWriteHandler(defaultStyleStrategy())
                    .registerConverter(new LongStringConverter())
                    .sheet(sheetName)
                    .doWrite(data);
        } catch (Exception e) {
            log.error("导出 Excel 失败: {}", filename, e);
            response.reset();
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"code\":500,\"message\":\"导出失败\"}");
        }
    }

    /** 表头灰底加粗居中 + 内容左对齐。字体微软雅黑 11 号。 */
    private static HorizontalCellStyleStrategy defaultStyleStrategy() {
        var headStyle = new WriteCellStyle();
        headStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headStyle.setHorizontalAlignment(HorizontalAlignment.CENTER);
        headStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        var headFont = new WriteFont();
        headFont.setBold(true);
        headFont.setFontName("微软雅黑");
        headFont.setFontHeightInPoints((short) 11);
        headStyle.setWriteFont(headFont);
        var contentStyle = new WriteCellStyle();
        contentStyle.setHorizontalAlignment(HorizontalAlignment.CENTER);
        contentStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        var contentFont = new WriteFont();
        contentFont.setFontName("微软雅黑");
        contentFont.setFontHeightInPoints((short) 11);
        contentStyle.setWriteFont(contentFont);
        return new HorizontalCellStyleStrategy(headStyle, contentStyle);
    }

    private static void setResponseHeaders(HttpServletResponse response, String filename,
                                           ExcelTypeEnum type) {
        String suffix = type == ExcelTypeEnum.CSV ? ".csv" : ".xlsx";
        String contentType = type == ExcelTypeEnum.CSV
                ? "text/csv; charset=UTF-8"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8) + suffix;
        response.setContentType(contentType);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + encoded + "\"");
    }

    /**
     * 读取 Excel/CSV 文件，返回数据列表。
     *
     * @param file 上传的文件
     * @param head 数据类（含 @ExcelProperty 注解）
     */
    public static <T> List<T> read(MultipartFile file, Class<T> head) throws IOException {
        try (InputStream in = file.getInputStream()) {
            return FesodSheet.read(in, head, null)
                    .autoCloseStream(false)
                    .doReadAllSync();
        }
    }
}
