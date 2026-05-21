package com.xuejiai.aaf.module.system.user.vo;

import java.time.LocalDateTime;

import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.apache.fesod.sheet.annotation.write.style.ColumnWidth;
import org.apache.fesod.sheet.annotation.write.style.ContentRowHeight;
import org.apache.fesod.sheet.annotation.write.style.HeadRowHeight;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 用户导出 VO。Fesod 需要 JavaBean 风格（getter/setter），record 不兼容。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@HeadRowHeight(20)
@ContentRowHeight(18)
@ColumnWidth(16)
@ExcelIgnoreUnannotated
public class UserExportVO {

    @ExcelProperty("ID")
    private Long id;

    @ExcelProperty("用户名")
    private String username;

    @ExcelProperty("昵称")
    private String nickname;

    @ExcelProperty("状态")
    private Integer status;

    @ExcelProperty("创建时间")
    @ColumnWidth(22)
    private LocalDateTime createTime;
}
