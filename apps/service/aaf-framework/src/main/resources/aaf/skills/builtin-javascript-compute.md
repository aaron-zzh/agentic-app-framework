---
code: builtin-javascript-compute
name: 受控 JavaScript 计算
summary: USE WHEN 用户要求基于明确输入完成复杂数值计算、统计汇总、结构化数据转换、排序去重或可验证算法处理，并需要实际运行得到确定结果；不用于系统命令、文件或网络访问，也不用于仅讲解代码。
version: 1
visibility: INTERNAL
toolAccessMode: RESTRICT
changeSummary: 内置初始版本
tool: script.execute.javascript | 100 | 将明确输入转换为最小纯 JavaScript 脚本并在 GraalVM 受限运行时执行，以真实结果完成确定性计算或数据处理
---
# 受控 JavaScript 计算

将用户提供的数据转换成最小纯 JavaScript 脚本，通过当前执行画像授权的受治理工具实际执行，并根据真实执行结果答复。

## 适用范围

- 复杂数值计算、统计汇总、单位换算或批量公式计算
- JSON 对象或数组的映射、筛选、排序、分组、聚合与去重
- 需要程序化复核的确定性算法和数据处理

## 执行规则

- 输入数据、计算规则或期望输出不明确时，先向用户澄清，不得猜测
- 只把任务所需的最小数据放入 `arguments`，脚本仅通过 `args` 读取参数
- 使用最小、确定性的标准 JavaScript，将最终结果赋给 `__result`
- 对象或数组结果必须先通过 `JSON.stringify` 序列化
- 必须以工具的真实返回值为准，不得用推测结果冒充执行结果
- 若脚本语法或运行失败，可依据 `stderr` 修正并重试一次；仍失败则如实说明
- 默认返回结果及必要的计算说明；用户要求时再附上生成的脚本

## 安全边界

- 只能调用 `script.execute.javascript`，不得改用 Shell、Python、Node.js 或其他执行路径
- 不得尝试访问宿主对象、文件、网络、环境变量、线程、进程或原生代码
- 不得在脚本中使用 `eval`、`Function` 构造器、WebAssembly 或动态加载代码
- 不得把凭证、密钥、令牌或非必要个人敏感信息放入脚本或参数
- 工具不可见、未授权或用户拒绝确认时必须停止，不得绕过 AAF 治理
