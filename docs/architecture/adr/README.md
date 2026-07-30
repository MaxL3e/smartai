# 架构决策记录

ADR（Architecture Decision Record）用于记录对系统长期有影响的技术与产品决策。每条决策一旦接受，后续实现默认遵循；如需改变，新增 ADR 取代旧决策，不直接抹除历史原因。

## 状态

- `提议`：正在讨论，不能作为实施依据。
- `接受`：已纳入当前实施基线。
- `废弃`：不再适用，但保留历史记录。
- `取代`：被另一条 ADR 替换。

## 当前决策

| ADR | 状态 | 决策 |
| --- | --- | --- |
| [0001](0001-production-architecture.md) | 接受 | 模块化单体业务核心 + 独立 AI 服务 |
| [0002](0002-governed-ai-decisions.md) | 接受 | 模型提取证据，确定性代码评分，高风险动作人工确认 |
| [0003](0003-system-of-record.md) | 接受 | PostgreSQL 作为业务事实源，搜索与缓存可重建 |
| [0004](0004-integration-first.md) | 接受 | API/事件连接器优先，RPA 仅作为受控兜底 |
| [0005](0005-embedded-ats-shell.md) | 接受 | 跨域 iframe + Embed SDK 作为 ATS 插件交付边界 |
| [0006](0006-first-implementation-stack.md) | 接受 | 首期统一使用 jOOQ 与 Celery + RabbitMQ |

## 模板

```markdown
# ADR-XXXX：决策标题

- 状态：提议 / 接受 / 废弃 / 取代
- 日期：YYYY-MM-DD
- 决策人：产品 / 架构 / 安全 / 业务

## 背景

## 决策

## 结果

## 备选方案

## 复审条件
```
