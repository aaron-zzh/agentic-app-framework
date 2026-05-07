
在数字界面中，颜色不仅是美学的载体，更是信息传递的重要工具。CSS Color Level 4 标准引入了 OKLCH 颜色空间, 提供​​感知均匀性​​（颜色差异与实际视觉感受一致），解决传统HSL/HSV在调整颜色时的不自然问题。文本帮助你快速掌握OKLCH的核心概念和应用逻辑。重点是理解其​​感知均匀性​​和​​与HSL的对比优势​​，这能帮助你在设计或代码中更科学地使用颜色。

![在这里插入图片描述](https://i-blog.csdnimg.cn/direct/2592d396bf5b4573b406570eb89fce97.png)


## **一、传统颜色模型的局限性**

在CSS发展历程中，我们经历了RGB、HEX、HSL等多种颜色模型，但这些传统方案存在明显痛点：

1. RGB/HEX：
   - 反直觉的数值组合（如`#6ea3db`）
   - 无法表达广色域P3颜色
   - 缺乏可预测的亮度关系

![在这里插入图片描述](https://i-blog.csdnimg.cn/direct/3cad8cd8014249b38ba5730f7fccb540.png#pic_center =x240)


2. HSL：
   - 伪均匀色彩空间（实际亮度感知不一致）
   - 色调调整导致对比度失控（如`darken()`函数在Sass中的异常表现）
   - 不支持现代显示器的广色域

OKLCH的工作原理与HSL相似，但它比HSL更好地编码亮度。

![在这里插入图片描述](https://i-blog.csdnimg.cn/direct/de6b0c96b2434a75bcc2e70841761c38.png#pic_center =x400)


HSL的主要问题是它使用一个圆柱形的色彩空间。每个色调都有相同的饱和度（0-100%）。但在现实中，我们的显示器和眼睛对不同的色调有不同的最大饱和度。HSL 通过变形颜色空间和扩展颜色，以具有相同的最大值来隐藏这种复杂性。

## **二、OKLCH的优势**

### **更接近人眼感知的数学模型**

`oklch` 是一种定义CSS颜色的新方法。在`oklch(L C H)`或`oklch(L C H / a)`中，每项对应如下：

- L（Lightness）：0-1区间对应黑到白的真实亮度感知
- C（Chroma）：数值越大饱和度越高，其有用的最小值为 0，最大值无理论上限（但实际不超过 0.5）
- H（Hue）：0-360°色相角
- A（alpha）：是透明度（0-1或0-100%），1 对应 100%（完全不透明）

```css
a:hover {
  background:   oklch(0.45 0.26 264); /* blue */
  color:        oklch(1 0 0);     /* white */
  color:        oklch(0 0 0 / 50%); /* black with 50% opacity */
}
```

[**OKLCH颜色选择器和转换器**](https://oklch.com)
![在这里插入图片描述](https://i-blog.csdnimg.cn/direct/12dc93636a084bdaa9b49ae4a390d337.png#pic_center)


### **优势**

- OKLCH 将设计师从手动选择每种颜色的需要中解放出来。设计人员可以定义一个公式，选择一些颜色，然后自动生成整个设计系统调色板。
- OKLCH 支持宽色域P3色：比sRGB多30%色彩空间，可以使用OKLCH来指定这些新颜色。
- 与 HSL 不同，OKLCH 更适合颜色修改和调色板生成。它使用感知亮度，所以没有更多意想不到的结果。
- 可读性：与 rgb 或十六进制 `#ca0000` 不同，OKLCH是人类可读的。通过查看数字，您可以快速了解值代表的颜色。
- 无障碍设计：精准的亮度控制确保 OKLCH 提供更好的无障碍（a11y）支持

![在这里插入图片描述](https://i-blog.csdnimg.cn/direct/9a0e3b43dccd46288cdb729527a6f9bc.png#pic_center)

## **三、如何使用 OKLCH**

1. 通过 [OKLCH转换器](https://oklch.com/) 或 [自动脚本](https://github.com/fpetrakov/convert-to-oklch) 转换原颜色值，并替换原来的 `rgb()/hsl()`

```css
.header {
- background: #f3f7fa;
+ background: oklch(0.97 0.006 240);
}
```

2. 添加一个调色板

将颜色移动到调色板上来增加CSS代码的可维护性。

- 所有颜色都定义为 CSS 变量
- 组件只通过变量使用这些颜色, 如 `var(--error)`
- 设计师应该尝试重复使用颜色，以减少颜色变体的数量
- 对于深色或高对比度的主题，不添加 `@media`，只需在调色板中更改 CSS 自定义变量即可。

**示例**：

```css
:root {
  --surface-0: oklch(0.96 0.005 300);
  --surface-1: oklch(1 0 0);
  --surface-2: oklch(0.99 0 0 / 85%);
  --surface-2-shadow: oklch(0 0 0 / 6%);
  --surface-3d: oklch(0.96 0.005 300);
  --surface-ui-regular: oklch(0.7 0.05 310 / 18%);
  --surface-ui-accent: oklch(0.69 0.17 286 / 20%);
  --surface-ui-warning: oklch(0.7 0.18 80 / 20%);
  --surface-ui-danger: oklch(0.79 0.18 3 / 20%);
  --surface-badge: oklch(0.32 0 0 / 85%);
  --text-primary: oklch(0 0 0);
  --text-secondary: oklch(0.54 0 0);
  --text-note: oklch(0.85 0 0);
  --text-badge: oklch(1 0 0);
  --chess: oklch(0.94 0 0);
  --ui-bg: oklch(0.7 0.05 310 / 18%);
  --ui-border: oklch(0.7 0 0 / 20%);
  --accent: oklch(0.57 0.18 286);
  --danger: oklch(0.59 0.23 7);
  --border-p3: oklch(1 0 0 / 50%);
  --border-rec2020: oklch(1 0 0 / 75%);
}

@media (prefers-color-scheme: dark) {
  :root {
    --surface-0: oklch(0 0 0);
    --surface-1: oklch(0.29 0.01 300);
    --surface-2: oklch(0.29 0 0 / 85%);
    --surface-2-shadow: oklch(0 0 0 / 40%);
    --surface-3d: oklch(0.29 0.01 300);
    --text-primary: oklch(1 0 0);
    --text-note: oklch(0.45 0 0);
    --chess: oklch(0.38 0.01 297);
    --ui-border: oklch(0.7 0 0 / 50%);
    --border-p3: oklch(0 0 0 / 30%);
    --border-rec2020: oklch(0 0 0 / 50%);
  }
}
```

## **四、未来展望**

随着 CSS Color Module Level 5  的临近，OKLCH将解锁更多可能：

1. 原生颜色操作：`oklch(from var(--color) calc(l+0.1) c h)`
2. 动态主题引擎：基于亮度感知的自动对比度调整
3. 跨媒体一致性：印刷/屏幕的色彩空间统一

## **五、总结**

OKLCH不仅是一个新的颜色格式，更是开启现代色彩工程的钥匙。它解决了传统模型的三大痛点：人机交互的直觉性、广色域的未来兼容性、设计系统的可维护性。在Apple设备覆盖率超60%的今天，拥抱OKLCH就是为下一个十年的Web体验打下基石。

## **立即体验**：[OKLCH颜色选择器和转换器](https://oklch.com) ｜ [调色板生成器](https://huetone.ardov.me)

## 参考资料

- 《CSS中的OKLCH：为什么我们从RGB和HSL转向OKLCH》 : <https://evilmartians.com/chronicles/oklch-in-css-why-quit-rgb-hsl>
- <https://developer.mozilla.org/zh-CN/docs/Web/CSS/color_value/oklch>

## END
