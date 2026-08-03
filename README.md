# 知聘 · 招聘智能体体验平台

面向央国企招聘场景的可解释招聘智能体产品基线。当前以可独立使用的招聘智能体为主，优先完成招聘任务、企业知识、独立简历库、人才匹配、名单确认、推荐报告与运行审计；ATS 与在线面试平台仅保留后续扩展接口。

- 在线体验：[https://maxl3e.github.io/smartai/](https://maxl3e.github.io/smartai/)
- 技术预留：ATS 嵌入协议模拟台（不属于当前验收范围）：[https://maxl3e.github.io/smartai/apps/host-harness/](https://maxl3e.github.io/smartai/apps/host-harness/)
- 当前实现：G1-G3 已接入本地 Core API；G3 使用 12 位虚构候选输入，知识、名单确认和推荐报告仍在后端化，在线面试为接口预留
- 数据说明：演示数据均为虚构数据，不包含真实候选人信息

## 产品目标

本项目当前用于交付可独立使用的招聘智能体，而不是绑定或重建某一家招聘系统。HR 可直接在本系统维护知识、导入简历并完成推荐闭环；待核心能力稳定后，再通过适配层连接客户的岗位、简历、面试、消息和审批接口。

核心原则：

- 智能体负责检索、生成、评分、提醒和报告草拟
- 岗位发布、候选名单、淘汰决定和录用决定必须由人工确认
- 推荐结论能够追溯到评分规则、简历证据和企业知识
- 知识库独立维护，工作台只展示当前任务状态和待办，不重复堆放知识管理内容
- 所有智能体动作、知识引用和人工修改均保留审计记录

## 推荐演示流程

1. 在“招聘任务”中通过自然语言描述招聘需求并创建任务。
2. 查看智能体生成的岗位描述、任职标准和人才推荐评分卡，人工调整后确认岗位方案。
3. 进入“人才匹配”，查看候选人排序、分项评分、简历原文证据和待核实项。
4. 选择候选人并保存推荐名单草稿；下一阶段将实现服务端确认版本与推荐报告。
5. 在“知识库”中维护历史 JD、用人标准和人才画像。
6. 在“运行审计”中回看智能体执行、知识引用和人工操作全过程。
7. “面试协同”和“综合评价”当前仅展示后续接口边界，不执行外部动作。

## 页面与能力

| 页面 | 主要能力 |
| --- | --- |
| 智能体工作台 | 展示招聘阶段、当前产出、待人工确认事项和实时智能体动态 |
| 招聘任务 | 对话式创建任务、搜索筛选、任务编辑、阶段推进和归档恢复 |
| 岗位方案 | 生成和编辑 JD、任职标准、评分卡、推荐阈值及知识来源 |
| 人才匹配 | 简历库检索、策略调整、候选人排序、证据解释和名单确认 |
| 面试协同 | 后续接口预留；当前不发送邀请，不调用在线面试或消息平台 |
| 综合评价 | 后续能力；当前不使用虚构面试分生成综合评价 |
| 知识库 | 岗位知识、人才画像、制度流程的新增、筛选、编辑、版本和归档维护 |
| 运行审计 | 按任务查看智能体动作、知识引用、人工操作并导出 CSV |

平台同时提供全局搜索、通知中心、企业空间、帮助中心、个人操作入口以及桌面端和移动端响应式布局。

## 知识库边界

知识库是独立的维护空间，不在智能体工作台重复展示资料列表。知识内容会在真正需要上下文的位置按需出现：

- 岗位方案引用历史 JD、岗位标准和制度流程
- 人才匹配引用人才画像、评分卡和简历证据
- 综合评价引用面试结果、评价标准和人工复核记录
- 运行审计记录每次知识引用及对应版本

当前前端已实现知识资料的增删改查、筛选、复核、版本展示和归档交互。真实文件解析、向量检索、权限隔离和知识服务 API 需要在后端接入阶段实现。

## 当前实现范围

已实现：

- G1-G3 可点击业务链路、名单草稿和页面状态联动
- 对话式招聘任务创建与岗位方案生成体验
- 可编辑的岗位评分卡、权重和推荐阈值
- 候选人级匹配证据、简历查看和名单选择
- 面试协同与综合评价预留页，明确展示输入、输出和当前执行边界
- 知识资料维护、运行审计及常用导出功能
- 浏览器本地持久化和响应式布局
- 可复用的 iframe Embed SDK、精确 Origin 消息握手和宿主能力协商
- ATS 岗位侧栏、候选人侧栏和全页工作区联调台
- 招聘业务 OpenAPI、跨运行时 AsyncAPI 和接口总清单
- Java 21 + Spring Boot 4.0.x + Spring Modulith 2.0 的 Core API 安全、数据迁移和模块化地基
- 招聘需求 G1 后端闭环：自然语言草案、补充修订、字段置信度、人工确认、招聘任务持久化和前端 API 接入
- G1 的幂等、乐观锁、租户隔离、过期处理、确认哈希、原始输入加密和人工确认记录
- 岗位方案 G2 后端闭环：确定性方案生成、JD 与评分卡版本修改、审核门禁、人工批准和 AgentRun 查询
- 人才匹配 G3 后端闭环：候选输入标准化、不可变简历版本、硬条件过滤、固定评分、逐项原文证据、匹配运行和任务候选人持久化
- 嵌入场景创建需求时可将 ATS 岗位引用和已鉴权宿主上下文哈希写入服务端草案

暂未实现：

- 真实大模型、RAG、向量数据库和文档解析服务
- 客户 ATS、人才库、在线面试、短信邮件和审批系统的真实连接器实现
- G2 的真实 LLM/RAG 生成与知识引用；当前生成器为 `DETERMINISTIC_DEMO`，不会调用大模型或知识检索
- 后续招聘业务 endpoint：知识检索、候选名单确认、面试编排、结果回收和综合评价
- 真实用户认证、组织数据范围、生产环境 API adapter 和 PostgreSQL 部署验收
- 已配置并完成验收的 PostgreSQL 生产实例及完整后端业务闭环
- 真实候选人数据处理与生产环境合规能力

## 技术栈

- React
- Vite
- Lucide React
- 浏览器 `localStorage`
- GitHub Actions + GitHub Pages
- OpenAPI 3.1 + AsyncAPI 3.0
- Java 21 + Spring Boot 4.0.x + Spring Modulith 2.0
- Spring Security + Flyway；PostgreSQL 是目标事实源，当前未连接生产实例

主要代码集中在：

```text
src/App.jsx                         页面、状态、演示业务逻辑与嵌入 Shell
src/styles.css                      全局设计系统与响应式布局
apps/host-harness/                  ATS 宿主联调台
apps/core-api/                      Core API 安全、Flyway 与模块化工程地基
packages/embed-sdk/                 iframe 生命周期与宿主消息 SDK
packages/contracts/openapi/         招聘业务和嵌入会话 HTTP 契约
packages/contracts/asyncapi/        Webhook、领域事件和 AI 消息契约
scripts/validate-contracts.mjs      契约完整性校验
```

## 本地运行

环境要求：Node.js 22 或兼容版本。

```bash
npm install
npm run dev
```

Vite 默认地址为 `http://127.0.0.1:5173/`。如端口已占用，请以终端实际输出为准。

本地 ATS 嵌入联调台位于：

```text
http://127.0.0.1:5173/apps/host-harness/
```

模拟台支持岗位侧栏、候选人侧栏、全页工作区、上下文切换、主题令牌和会话续期协议。它没有 ATS 后端、认证服务或真实令牌，“连接”“续期”“上下文接收”等状态只模拟消息时序，不代表服务端已经完成身份认证、令牌签发、权限交集或资源映射。真实客户环境必须由 ATS 后端创建会话并通过平台校验后才能显示业务数据。

生产构建与本地预览：

```bash
npm run build
npm run preview
```

GitHub Pages 专用构建：

```bash
npm run build:pages
```

接口和 Embed SDK 回归校验：

```bash
npm run contracts:check
npm run test:embed
```

### Core API 本地验证

`apps/core-api/` 已实现招聘智能体前三个服务端纵向切片：G1 负责将自然语言需求整理为可确认草案并创建招聘任务；G2 负责生成、修改和人工批准岗位方案与评分卡；G3 负责接收候选输入、生成不可变简历版本，并执行硬条件过滤、确定性加权评分和逐项证据定位。前端的“新建招聘任务”“岗位方案”和“人才匹配”已接入这些接口；服务异常会明确显示错误，不会把固定数据冒充服务结果。G2 使用 `DETERMINISTIC_DEMO`，G3 使用 `DETERMINISTIC_RULES`，两者均未调用真实 LLM、RAG 或企业知识检索。

Windows PowerShell：

```powershell
cd apps/core-api
.\mvnw.cmd clean test
.\mvnw.cmd -Dspring-boot.run.profiles=local spring-boot:run
```

Linux/macOS：

```bash
cd apps/core-api
./mvnw clean test
./mvnw -Dspring-boot.run.profiles=local spring-boot:run
```

本地服务默认监听 `http://127.0.0.1:8080/`，当前可用探针为 `/actuator/health`。这些命令必须显式使用 `local` profile；该 profile 使用本地测试数据库，不代表 PostgreSQL 生产连接已经完成。

部署时必须显式启用 `production` profile，并提供 `SMARTAI_DATABASE_URL`、`SMARTAI_DATABASE_RUNTIME_USERNAME`、`SMARTAI_DATABASE_RUNTIME_PASSWORD`、`SMARTAI_DATABASE_MIGRATION_USERNAME` 和 `SMARTAI_DATABASE_MIGRATION_PASSWORD`。任一必填配置缺失都必须令应用启动失败，严禁回退到 `local` profile、H2 或隐式默认凭据。

## 数据与重置

服务端创建的招聘需求草案、招聘任务、G2 岗位方案、标准化候选输入、简历版本、G3 匹配运行与结果保存在 Core API 数据库中。当前 G3 页面导入的 12 位候选人均为明确标记的虚构样本；下一阶段由独立简历库和文件解析链路替换演示输入适配器。推荐名单草稿、知识资料和前端审计日志仍保存在当前浏览器的 `localStorage` 中；清理站点存储后，这些演示数据会恢复为内置状态。面试状态与综合评价结论当前不生成、不模拟。

不要在本演示环境录入真实候选人简历、联系方式或其他敏感信息。

## 分支与发布

项目采用双分支流程：

- `test`：日常开发、集成和验收
- `main`：稳定演示与 GitHub Pages 发布

每次修改应先在 `test` 完成构建和浏览器验证，再使用 `--no-ff` 合并到 `main`。推送 `main` 后，`.github/workflows/deploy-pages.yml` 会自动执行 `npm run build:pages` 并发布 GitHub Pages；发布完成后再将 `main` 快进同步回 `test`。

最低验证要求：

```bash
npm run build
git diff --check
```

涉及界面时，还需检查常见桌面和移动端视口，并确认浏览器控制台没有应用错误。

## 后续接入建议

建议将正式接入拆为四层：

1. 招聘系统适配层：岗位、简历、候选人状态和审批接口。
2. 智能体编排层：任务状态机、人工确认节点、重试和异常处理。
3. 知识与评价层：文档解析、版本管理、检索、固定评分和证据映射。
4. 审计与安全层：权限、脱敏、调用留痕、数据保留和合规策略。

前端演示中的页面结构、状态和操作反馈可作为后续 API 契约与后端数据模型设计的基础。

## 实施文档

生产化方案已经进入仓库，统一入口见 [docs/README.md](docs/README.md)：

当前交付主形态是独立招聘智能体；ATS 嵌入与在线面试工具对接统一延期到核心业务闭环稳定之后，现有 Embed SDK 和契约仅作为技术预留。

- [产品需求](docs/product/PRD.md)
- [招聘任务状态与业务流程](docs/product/workflow.md)
- [系统架构](docs/architecture/system-design.md)
- [领域模型](docs/architecture/domain-model.md)
- [客户系统集成契约](docs/api/integration-contract.md)
- [ATS 宿主嵌入契约](docs/api/embed-contract.md)
- [完整接口清单](docs/api/interface-inventory.md)
- [OpenAPI 3.1 契约](packages/contracts/openapi/smartai-core-v1.json)
- [AsyncAPI 3.0 契约](packages/contracts/asyncapi/smartai-events-v1.json)
- [MVP 实施清单](docs/roadmap/mvp-backlog.md)
