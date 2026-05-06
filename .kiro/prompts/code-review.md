审查代码变更，按 blocker/major/minor 分级列出问题，给出修复建议。

检查维度：
1. diff 中每行改动能否追溯到任务需求（有无无关改动）
2. 是否引入了任务未要求的抽象、接口层或配置项
3. 是否违反 AAF 分层规则（controller → application → domain → gateway）
