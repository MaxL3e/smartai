# 知聘实施文档

本目录是“知聘 · 招聘智能体体验平台”从前端演示走向可交付产品的实施基线。产品、研发、测试、实施和客户接口讨论应以这里的文档为准，演示页面仅用于验证交互，不作为后端规则和数据结构的唯一依据。

## 文档地图

| 文档 | 解决的问题 | 主要读者 |
| --- | --- | --- |
| [产品需求](product/PRD.md) | 做什么、不做什么、为谁解决什么问题、如何验收 | 产品、业务、项目负责人 |
| [业务流程](product/workflow.md) | 招聘任务如何流转、何时自动执行、何时必须人工确认 | 产品、研发、测试、实施 |
| [系统架构](architecture/system-design.md) | 系统如何拆分、如何部署、如何保证可靠性和安全性 | 架构、研发、运维、安全 |
| [领域模型](architecture/domain-model.md) | 核心实体、聚合边界、状态、版本和审计约束 | 前后端、数据、测试 |
| [集成契约](api/integration-contract.md) | 如何连接客户 ATS、人才库、面试、消息和审批系统 | 后端、实施、客户 IT |
| [ATS 嵌入契约](api/embed-contract.md) | 如何将智能体安全嵌入客户 ATS 并传递身份、上下文、主题和导航 | 前端、后端、客户 ATS、安全 |
| [接口总清单](api/interface-inventory.md) | 每类业务输入输出的协议、方向、权威源、权限门禁、版本和优先级 | 产品、前后端、实施、测试 |
| [OpenAPI 3.1](../packages/contracts/openapi/smartai-core-v1.json) | 嵌入会话和招聘核心 HTTP API 的机器可读契约 | 前后端、测试、客户 IT |
| [AsyncAPI 3.0](../packages/contracts/asyncapi/smartai-events-v1.json) | Webhook、领域事件、AI 命令结果、重试和 DLQ 的机器可读契约 | 后端、AI、集成、运维 |
| [MVP 实施清单](roadmap/mvp-backlog.md) | 12 至 16 周内先做什么、完成标准和风险是什么 | 项目经理、研发、测试 |

## 统一原则

1. 智能体可以生成、检索、评分、提醒和草拟报告，但不能绕过人工完成高风险招聘决定。
2. 模型负责理解和提取证据，业务状态、硬性过滤、评分公式和权限判断由确定性代码执行。
3. 每个推荐结论必须关联规则版本、知识版本、候选人原文证据和模型运行记录。
4. 外部系统写操作必须支持幂等、重试、人工补偿和审计，不以页面自动化作为默认集成方案。
5. PostgreSQL 是业务事实源，OpenSearch 和缓存均为可重建派生数据，不反向成为业务主数据。
6. 第一阶段以一个试点客户、一个简历来源、一个面试工具和一至两个岗位族为边界。
7. 生产主入口嵌入客户 ATS；独立站只用于演示、管理和受控降级。
8. 文档中出现冲突时，先更新产品需求和业务流程，再同步架构、领域模型、接口与测试用例。

## 变更要求

- 新增产品能力时，同时更新产品需求、业务流程和 MVP 清单。
- 新增实体或状态时，同时更新领域模型、接口契约和数据库迁移方案。
- 新增客户连接器时，必须补充能力矩阵、认证方式、限流、幂等和异常补偿说明。
- 修改人工门禁或评分规则时，必须经过产品、安全和业务负责人共同评审。
- 所有架构取舍以 ADR 形式记录，避免只存在于会议或聊天记录中。

## 下一阶段代码结构

在不影响现有演示的前提下，生产化代码按以下方向逐步演进：

```text
apps/
  web/                  React + TypeScript 共享业务模块与三种 Shell
  host-harness/         无后端认证的 ATS 宿主协议模拟与嵌入 E2E 测试壳
  core-api/             Java 21/Spring Boot 4.0.x/Spring Modulith 2.0 Core API 地基
services/
  ai-service/           FastAPI 模型、检索、解析与评价服务
packages/
  embed-sdk/            iframe 生命周期、会话、上下文和宿主消息协议
  contracts/            OpenAPI、事件和共享类型
docs/                   产品、架构、接口和实施基线
deploy/                 本地编排、Kubernetes 与环境配置
```

目录迁移应分阶段进行。当前 `src/` 前端继续作为可运行基线，在 API 契约和模块边界稳定前不做一次性重写。

当前仓库已落地 `apps/host-harness/`、`packages/embed-sdk/`、`packages/contracts/` 和 `apps/core-api/`。Core API 已完成 G1“需求草案 -> 人工确认 -> 招聘任务”、G2“岗位方案 -> 版本修改 -> 人工批准”和 G3“候选输入 -> 标准化简历 -> 硬过滤 -> 固定评分与证据”三个纵向切片，前端已调用这些服务。G2/G3 当前使用不调用 LLM/RAG 的确定性引擎；候选名单确认、面试、评价、知识检索、真实认证和 PostgreSQL 生产实例仍未完成。

提交前运行 `npm run test:agent`、`npm run contracts:check` 与 `npm run test:embed`。Core API 使用显式 `local` profile 启动并以干净构建验证：Windows 执行 `cd apps/core-api` 后运行 `.\mvnw.cmd clean test`，Linux/macOS 运行 `./mvnw clean test`；启动命令分别为 `.\mvnw.cmd -Dspring-boot.run.profiles=local spring-boot:run` 和 `./mvnw -Dspring-boot.run.profiles=local spring-boot:run`。

`production` profile 不得继承本地数据库兜底。缺少 `SMARTAI_DATABASE_URL`、运行时账号 `SMARTAI_DATABASE_RUNTIME_USERNAME/PASSWORD` 或迁移账号 `SMARTAI_DATABASE_MIGRATION_USERNAME/PASSWORD` 时必须启动失败，不能据此宣称已连接 PostgreSQL 生产环境。
