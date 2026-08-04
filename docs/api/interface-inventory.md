# ATS 可嵌入招聘智能体接口清单

> 文档状态：核心业务接口继续作为实施基线；ATS 与在线面试相关接口为后续预留
> 契约版本：`v1`
> 适用对象：产品、前端、后端、AI 服务、集成实施、客户 ATS、测试与安全团队

本文是招聘智能体的长期接口总账；当前先实施独立智能体核心接口，ATS 与在线面试接口的优先级仅在后续集成阶段生效。它回答每个业务输入从哪里来、输出到哪里去、谁拥有最终事实、谁有权执行、哪些动作必须人工确认，以及如何避免重复执行或旧版本覆盖。字段级定义以机器契约为准：

- `[OAS]`：[SmartAI Core OpenAPI v1](../../packages/contracts/openapi/smartai-core-v1.json)
- `[AAS]`：[SmartAI Events AsyncAPI v1](../../packages/contracts/asyncapi/smartai-events-v1.json)
- `[EMBED]`：[ATS 宿主嵌入契约](./embed-contract.md)
- `[INT]`：[客户系统集成契约](./integration-contract.md)
- `[DOMAIN]`：[领域模型](../architecture/domain-model.md)

表中的 ``[OAS] <operationId>`` 表示当前 OpenAPI 已定义的操作；``[OAS gap] <operationId>`` 表示该能力已进入完整接口清单、但仍需补入机器契约。`P0` 的 OAS gap 是实现阻断项，必须在对应功能开发前清零。

## 1. 阅读约定

### 1.1 方向与优先级

“方向”始终以 SmartAI 平台为参照：`入` 表示平台接收，`出` 表示平台发送，`双向` 表示双方都有独立的读写或消息动作。`宿主` 指客户 ATS 页面，`嵌入端` 指 SmartAI iframe，`连接器` 指客户 ATS、人才库、面试、消息或 OA 的服务端适配器。

| 优先级 | 定义 |
| --- | --- |
| `P0` | 首个客户试点闭环必需；缺失则不能完成“需求 -> 岗位方案 -> 匹配 -> 名单确认 -> 面试 -> 评价回流” |
| `P1` | 生产可运维、可扩展或次要业务动作；可在 P0 主链路稳定后交付 |
| `P2` | 扩展兼容或低频管理能力；不阻断首个试点 |

### 1.2 全局约束

1. HTTP 写操作必须携带 `Idempotency-Key`；更新、审批、确认、撤销和外部状态回写还必须携带 `If-Match` 或期望 `version`。传输幂等记录至少保留 72 小时，外部副作用另以业务唯一键覆盖完整业务有效期。
2. `postMessage` 只传资源引用、显示提示和 UI 意图，以 `sessionId + messageId + sequence + protocolVersion` 校验和去重。它不能代替岗位方案审批、名单确认、面试邀请或评价回写 API。
3. Webhook 以 `tenantId + connectorId + sourceEventId` 去重，先验签并保存 `WebhookReceipt`；消费者按外部资源版本或事件时间处理乱序，不允许状态倒退。
4. 异步消息以 `tenantId + consumerName + messageId` 做传输去重，以 `tenantId + businessKey` 做业务去重；事件 Schema 在同一主版本只允许兼容新增。
5. 简历正文、附件、知识正文、面试转写、联系人和模型完整输入输出不得进入 URL、`postMessage`、普通日志或消息内联载荷，只传受控资源引用和哈希。
6. `数据权威` 表示最终事实来源，不表示平台可以无条件写回。每次读取、解密、导出、模型调用、人工修改、外部写入和门禁决策都必须产生不可变审计事件。
7. 首批后端接口必须显式声明并测试适用错误状态：`400` 请求校验、`401` 身份或令牌无效、`403` 权限/数据范围/Origin 拒绝、`404` 资源不可见或映射失败、`409` 版本/幂等/重放冲突、`410` 临时资源过期、`428` 缺少 `If-Match`、`429` 限流，以及 `500/503/504`。所有错误复用 `ErrorEnvelope`，禁止将内部堆栈、令牌或个人信息写入响应。

## 2. 嵌入会话、上下文与导航

| ID | 输入/输出 | 协议与方向 | 数据权威 | 权限/人工门禁 | 幂等/版本要求 | MVP | 契约引用 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| EMB-01 | 创建一次嵌入会话：用户委托断言、宿主 Origin、资源引用、初始路由、能力和主题 | HTTPS REST，ATS 后端 -> 平台（入） | 身份/组织由客户 IdP/ATS 权威；会话与有效权限由平台权威 | ATS 服务身份 + 用户委托；校验租户、用户映射、Origin、资源范围和 scope；失败关闭 | `Idempotency-Key`；上下文含 `contextVersion/nonce/expiresAt`；单次 `bootstrapToken` | P0 | `[OAS] createEmbedSession`；[EMBED §4](./embed-contract.md#4-会话与令牌) |
| EMB-02 | 返回 `sessionId/embedUrl/bootstrapToken/required/supported/effective/missing` | HTTPS REST，平台 -> ATS 后端（出） | 平台 | 只返回调用方可用能力；不得返回业务正文 | 响应绑定 session、client、host Origin 和协议版本；令牌 30-60 秒单次有效 | P0 | `[OAS] createEmbedSession`；[EMBED §7](./embed-contract.md#7-能力协商) |
| EMB-03 | 交换引导令牌并取得嵌入访问令牌、已解析上下文和当前会话 ETag | HTTPS REST，iframe -> 平台（入/出） | 平台会话与权限投影 | iframe 必须提交从 `host.init` 的 `event.origin` 观察到的 `observedParentOrigin`；令牌绑定 `aud/tenant/sub/session/embedClient/hostOrigin/jti/nonce`；禁止宿主 JS 读取访问令牌 | 引导令牌单次消费；访问令牌 5-10 分钟；重复交换拒绝；观察 Origin 必须与会话精确一致；ETag 对应当前 `contextVersion` | P0 | `[OAS] exchangeEmbedToken`；[EMBED §4](./embed-contract.md#4-会话与令牌) |
| EMB-04 | 申请会话续期并返回新的一次性令牌 | `postMessage` + ATS 后端 REST，iframe -> 宿主 -> 平台 -> iframe（双向） | ATS 当前登录态与平台会话共同约束 | 仅具备 `AUTH_REFRESH` 能力的宿主；敏感动作在续期期间暂停 | `replyTo` 对应请求 `messageId`；新令牌单次有效；旧令牌不可复用 | P0 | `[EMBED] session.renew.request/response`；`[OAS] createEmbedSession` |
| EMB-05 | 初始化消息：上下文、主题、区域语言、宿主能力 | `MessageChannel`，宿主 -> iframe（入） | 签名 token 中身份/权限为权威；消息仅是 UI 上下文 | 精确校验 `origin/event.source/session/schema`；资源引用再次做服务端鉴权 | `messageId + sequence + contextVersion`；旧序号和旧上下文拒绝 | P0 | [EMBED §5-6](./embed-contract.md#5-hostcontext) |
| EMB-06 | 上下文切换：任务、岗位、候选人或 Application 引用 | `MessageChannel` 传递 UI 意图；iframe 通过 HTTPS REST 解析并替换服务端上下文（双向） | 外部资源身份由 ATS 权威；解析、映射、数据范围和活动会话上下文由平台权威 | 切换前检查未保存编辑；服务端重新校验观察 Origin、租户、组织、资源权限和能力；无权/过期/无法映射时清空旧详情并拒绝 | 解析结果短期有效且包含 `resolutionId/contextHash`；解析响应返回当前会话 ETag，替换使用该 `ETag + Idempotency-Key + If-Match`；成功替换返回新 ETag；`contextVersion` 单调增加 | P0 | `[OAS] resolveEmbedContext/replaceEmbedContext`；`[EMBED] context.replace/context.accepted/context.rejected` |
| EMB-07 | 宿主打开智能体路由、切换可见性、更新主题、销毁实例 | `MessageChannel`，宿主 -> iframe（入） | 宿主页面状态权威；平台白名单路由/主题 token 权威 | 只接收白名单 route、theme 和 locale；任意 CSS/HTML/URL 拒绝 | 按消息 `sequence` 有序处理；`protocolVersion` 不兼容时失败关闭 | P0 | `[EMBED] route.open/theme.update/visibility.change/destroy` |
| EMB-08 | 请求宿主导航、报告路由、动作完成、脏状态、尺寸和错误 | `MessageChannel`，iframe -> 宿主（出） | 平台动作状态权威；真实 ATS URL 由宿主权威 | 导航只传资源引用与意图，由宿主生成 URL；错误载荷脱敏 | 请求/响应用 `messageId/replyTo` 关联；重复完成事件不重复触发宿主动作 | P0 | `[EMBED] navigation.requested/route.changed/action.completed/dirty.changed/resize.request/error` |
| EMB-09 | 创建一次性独立页 deep link | HTTPS REST，iframe -> 平台（入/出） | 平台 | 仅 `OPEN_STANDALONE` 能力与有效会话；降级打开需审计 | `Idempotency-Key`；60 秒单次 opaque URL，不含 token/PII | P1 | `[OAS] createEmbedDeepLink`；[EMBED §8](./embed-contract.md#8-导航主题与布局) |

## 3. 招聘任务与岗位方案

| ID | 输入/输出 | 协议与方向 | 数据权威 | 权限/人工门禁 | 幂等/版本要求 | MVP | 契约引用 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| REC-01 | 输入自然语言招聘需求，创建需求草案并解析结构化意图 | HTTPS REST，嵌入端 -> 平台（入） | 原始输入由 HR 权威；解析草案由平台权威 | 招聘 HR；此时不创建正式任务，不触发外部写入 | `Idempotency-Key`；保存输入哈希、解析器/模型版本和草案 `version`；重放响应以 `Idempotency-Replayed` 标识 | P0 | `[OAS] createRequirementDraft`；[DOMAIN §6.1](../architecture/domain-model.md#61-requirementdraft) |
| REC-02 | 返回解析后的岗位、人数、地点、职责、条件、缺失项和澄清问题 | HTTPS REST，平台 -> 嵌入端（出） | 平台派生结果；HR 对业务含义最终确认 | 输出必须可编辑、可追溯，不得把推断当作已确认事实 | 响应引用草案版本、AgentRun 和证据；刷新不重跑 | P0 | `[OAS] getRequirementDraft`；`[AAS] aiResultsV1` |
| REC-03 | 修改草案或补充自然语言信息 | HTTPS REST，嵌入端 -> 平台（入） | HR 输入权威；平台保存版本 | 招聘 HR；不能编辑其他组织草案 | `Idempotency-Key + If-Match`；新建草案版本，冲突返回 409 | P0 | `[OAS] updateRequirementDraft` |
| REC-04 | 人工确认草案并创建招聘任务 | HTTPS REST，嵌入端 -> 平台（入/出） | 平台 `RecruitmentTask` 权威 | 明确确认门禁；记录确认人、时间、草案版本和理由 | `Idempotency-Key + If-Match`；`tenant + draftId` 业务唯一 | P0 | `[OAS] convertRequirementDraft`；[DOMAIN §6.2](../architecture/domain-model.md#62-recruitmenttask) |
| REC-05 | 查询任务列表、详情、执行状态和待确认摘要 | HTTPS REST，ATS/嵌入端 -> 平台（出） | 平台 | 按租户、组织、岗位和用户数据范围过滤 | 游标分页；返回任务 `version`、当前主 AgentRun 和 checkpoint 数量 | P0 | `[OAS] listRecruitmentTasks/getRecruitmentTask` |
| REC-06 | 暂停、恢复、取消、归档或恢复任务 | HTTPS REST，嵌入端 -> 平台（入） | 平台状态机权威 | HR/招聘负责人；归档/恢复记录原因；不可逆外部动作不自动撤销 | `Idempotency-Key + If-Match`；状态机校验；重复命令返回首次结果 | P1 | `[OAS] updateRecruitmentTask/commandRecruitmentTask`；[DOMAIN §6.3](../architecture/domain-model.md#63-任务状态机) |
| REC-07 | 基于需求、历史 JD、用人标准和人才画像生成岗位方案/评分卡 | HTTPS REST + AMQP，嵌入端 -> Core -> AI（入/出） | 平台版本化岗位方案权威；知识文档仅作证据 | 招聘 HR 可发起；生成结果进入待确认，不可直接发布 | HTTP `Idempotency-Key`；AI `businessKey/inputHash/workflowVersion/resultSchemaRef` | P0 | `[OAS] generatePositionPlan`；`[AAS] aiCommandsV1/aiResultsV1` |
| REC-08 | 查询岗位方案、评分项、硬条件、推荐阈值和知识引用 | HTTPS REST，嵌入端 -> 平台（出） | 平台 | 任务可见范围；引用需校验知识版本 ACL | 返回不可变 `PositionPlanVersion/ScorecardVersion` 和引用版本 | P0 | `[OAS] getCurrentPositionPlan/getPositionPlanVersion`；[DOMAIN §6.4](../architecture/domain-model.md#64-positionplan-与-scorecard) |
| REC-09 | 编辑岗位描述、用人标准、评分卡、权重和硬条件 | HTTPS REST，嵌入端 -> 平台（入） | HR 确认后的平台版本权威 | 招聘 HR/负责人；总权重和规则合法性校验；保留 AI 建议与人工修改差异 | `Idempotency-Key + If-Match`；每次编辑创建新版本，不原地覆盖已批准版 | P0 | `[OAS] updatePositionPlanVersion` |
| REC-10 | 提交并批准岗位方案 | HTTPS REST，嵌入端 -> 平台（入） | 平台门禁记录权威 | 用人经理/招聘负责人按策略审批；创建 `HumanCheckpoint` | `Idempotency-Key + If-Match`；批准绑定精确方案/评分卡版本 | P0 | `[OAS] requestPositionPlanReview/decideHumanCheckpoint`；`[AAS] domainEventsV1(PositionPlanApproved.v1)` |
| REC-11 | 提交岗位发布审批：公开内容、目标连接器、发布时间、外部版本和客户审批引用 | HTTPS REST，嵌入端 -> 平台（入/出） | 平台 G3 门禁权威；客户 OA 审批结果仍由 OA 权威 | 客户审批人；G2 方案确认不得替代 G3 发布审批；确认前展示公开内容和外部影响 | `Idempotency-Key + If-Match`；checkpoint 绑定方案版本、连接器、公开内容哈希和审批引用 | P0 | `[OAS] requestJobPublicationReview/decideHumanCheckpoint` |
| REC-12 | 将已批准岗位创建/更新/发布到客户 ATS | HTTPS REST/连接器命令，平台 -> ATS（出） | ATS 岗位及发布状态权威；平台批准方案权威 | 必须具备 `JOB_CREATE/JOB_PUBLISH`，且引用当前方案已批准的 G3 `PUBLISH_JOB` checkpoint；发布为高风险人工门禁 | HTTP 幂等 + `tenant/connector/operation/task/planVersion` 业务唯一；外部版本冲突转人工 | P0 | `[OAS] publishPositionPlanVersion`；`[INT] POST/PUT /jobs, POST /jobs/{jobId}/publish`；`[AAS] connectorResultsV1` |

## 4. 知识库

| ID | 输入/输出 | 协议与方向 | 数据权威 | 权限/人工门禁 | 幂等/版本要求 | MVP | 契约引用 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| KNO-01 | 创建知识文档元数据：类型、组织范围、标签、有效期和来源 | HTTPS REST，知识管理员 -> 平台（入） | 平台元数据权威；外部来源标识由源系统权威 | 知识管理员；组织/岗位范围最小授权 | `Idempotency-Key`；来源系统 + externalId + sourceVersion 唯一 | P0 | `[OAS] createKnowledgeDocument`；[DOMAIN §9.1](../architecture/domain-model.md#91-knowledgedocument) |
| KNO-02 | 获取上传凭据并上传 JD、制度、画像或历史用人文档 | HTTPS REST + 加密对象存储，客户端 -> 平台（入） | 原文件由上传来源权威；对象元数据由平台权威 | MIME、大小、病毒和敏感级别检查；上传不等于发布 | 上传会话幂等；完成时校验 `sha256/size/objectKey`；同内容可去重 | P0 | `[OAS] createKnowledgeUploadSession/completeKnowledgeUploadSession/createKnowledgeVersion` |
| KNO-03 | 解析、分块和索引知识版本并返回处理状态 | AMQP + HTTPS，Core <-> AI；嵌入端查询（双向） | 原文版本不可变；派生 chunk/index 由平台权威 | AI 仅读取获授权对象引用；失败可重试但不自动发布 | `documentId + version + parser/indexerVersion` 业务键；输出含哈希和模型版本 | P0 | `[AAS] aiCommandsV1(DOCUMENT_PARSE/KNOWLEDGE_INDEX)`；`[OAS] getKnowledgeVersion` |
| KNO-04 | 查询、筛选和预览知识文档/版本/处理状态 | HTTPS REST，嵌入端 -> 平台（出） | 平台 | 知识管理员可看原文；普通 HR 只看授权范围和脱敏片段 | 游标分页；返回文档 `version` 与不可变内容版本号 | P0 | `[OAS] listKnowledgeDocuments/getKnowledgeDocument/listKnowledgeVersions/getKnowledgeVersion` |
| KNO-05 | 编辑元数据、创建新内容版本、替换有效期 | HTTPS REST，知识管理员 -> 平台（入） | 平台 | 知识管理员；已发布版本不原地修改 | `Idempotency-Key + If-Match`；新内容生成 `KnowledgeVersion` | P0 | `[OAS] updateKnowledgeDocument/createKnowledgeVersion` |
| KNO-06 | 审核并发布、停用或归档知识版本 | HTTPS REST，知识管理员 -> 平台（入） | 平台发布状态权威 | 发布需人工审核；停用影响新运行，不篡改历史引用 | `Idempotency-Key + If-Match`；checkpoint 绑定内容哈希和版本 | P0 | `[OAS] requestKnowledgeVersionReview/decideHumanCheckpoint/deactivateKnowledgeVersion` |
| KNO-07 | 将知识文档绑定到组织、岗位类型或招聘任务 | HTTPS REST，知识管理员/HR -> 平台（入/出） | 平台绑定关系权威 | 绑定双方资源都需可见；任务级绑定由任务负责人确认 | `Idempotency-Key + If-Match`；作用域 + 文档版本唯一 | P1 | `[OAS gap] upsertKnowledgeBinding/listKnowledgeBindings` |
| KNO-08 | 按授权范围检索知识并返回可验证引用 | HTTPS REST/AMQP，Core -> AI -> Core（入/出） | 检索结果为派生事实；原文与版本由知识库权威 | 仅为具体业务目的读取；引用必须回指 document/version/chunk/sourceLocator | `queryHash + scopeHash + indexVersion`；结果记录 embedding/reranker 版本 | P0 | `[AAS] aiCommandsV1(KNOWLEDGE_RETRIEVE/EVIDENCE_EXTRACT)`；`[OAS] resolveKnowledgeEvidence` |

## 5. 候选检索与匹配

| ID | 输入/输出 | 协议与方向 | 数据权威 | 权限/人工门禁 | 幂等/版本要求 | MVP | 契约引用 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MAT-01 | 从 ATS/人才库按结构化条件检索候选人和简历 | HTTPS REST，平台 -> 连接器（出） | 候选人/简历原始事实由 ATS/人才库权威 | `RESUME_SEARCH` + 候选授权范围；禁止模型生成自由 SQL | `Idempotency-Key`；稳定游标、`updatedAfter`、查询哈希和来源版本 | P0 | `[INT] POST /resume-searches`；[INT §6.3](./integration-contract.md#63-候选人与简历库) |
| MAT-02 | 上传或同步候选、简历版本和受控附件引用 | HTTPS REST/Webhook/文件，独立工作台或连接器 -> 平台（入） | 独立上传由平台权威；ATS/人才库同步数据由来源系统权威；平台保留不可变来源版本 | PDF/DOC/DOCX/TXT 白名单、大小与内容校验、最小字段和目的限制；写入搜索索引前脱敏 | `Idempotency-Key + SHA-256 + fileVersion/sourceVersion`；旧版本不覆盖新版本 | P0 | `[OAS] createResumeFile/listResumeFiles/getResumeFile`；`[INT] GET /candidates, /resumes/{resumeId}`；`[AAS] integrationNormalizedV1` |
| MAT-03 | 发起匹配运行：任务、岗位方案版本、候选范围和策略 | HTTPS REST，嵌入端 -> 平台（入） | 平台 `MatchRun` 权威 | 招聘 HR；仅批准或明确允许试算的方案版本；候选范围需授权 | `Idempotency-Key + If-Match`；记录 input snapshot/hash、算法/规则版本 | P0 | `[OAS] createMatchRun`；[DOMAIN §7.3](../architecture/domain-model.md#73-matchrun) |
| MAT-04 | 执行召回、证据提取与结构化信号生成 | AMQP，Core -> AI -> Core（双向） | 原始简历由来源权威；证据定位/抽取为平台派生 | AI 输入去除不必要 PII；只允许声明的结果 Schema | `businessKey + inputHash + workflowVersion`；每步 Inbox/Outbox 幂等 | P0 | `[AAS] aiCommandsV1(TALENT_RETRIEVE/EVIDENCE_EXTRACT)`、`aiResultsV1` |
| MAT-05 | 应用硬条件、固定权重评分和推荐等级 | 平台内部命令/领域事件（内部） | 确定性规则执行结果由平台权威 | 模型不得自由决定总分、硬淘汰或推荐阈值；规则版本可审计 | `taskCandidate + resumeVersion + scorecardVersion + algorithmVersion` 唯一 | P0 | `[DOMAIN] MatchResult`；`[AAS] domainEventsV1(MatchRunCompleted.v1)` |
| MAT-06 | 查询匹配运行进度、失败摘要和候选排序结果 | HTTPS REST，嵌入端 -> 平台（出） | 平台 | 任务数据范围；默认脱敏联系方式；失败项可见范围受控 | 游标分页；结果绑定 MatchRun/简历/评分卡版本；完成结果不可原地改写 | P0 | `[OAS] getMatchRun/listMatchResults` |
| MAT-07 | 查看单候选评分拆解、硬条件、证据和缺失信息 | HTTPS REST，嵌入端 -> 平台（出） | 平台派生结果；引用原文由 ATS/人才库权威 | 候选人级授权；每个得分必须有规则或证据引用；禁止无依据结论 | 返回结果 `version`、evidence hash/source locator 和模型/规则版本 | P0 | `[OAS] listMatchResults/getMatchResult`；[DOMAIN §7.4](../architecture/domain-model.md#74-matchresult-与证据) |
| MAT-08 | HR 标记复核、纠正证据或添加人工说明 | HTTPS REST，嵌入端 -> 平台（入） | 人工复核事实由平台权威；不篡改原模型输出 | 招聘 HR；记录前后值、原因和操作者；不得修改源简历 | `Idempotency-Key + If-Match`；新增 review/override 版本 | P1 | `[OAS gap] reviewMatchResult` |
| MAT-09 | 基于新简历、方案或知识版本重新匹配 | HTTPS REST，嵌入端 -> 平台（入） | 新 MatchRun 权威，历史运行保留 | 招聘 HR；明确显示变化原因和版本差异 | 新运行 `Idempotency-Key`；禁止覆盖旧结果；相同输入可复用已完成结果 | P1 | `[OAS] createMatchRun` |

## 6. 名单确认与人工门禁

| ID | 输入/输出 | 协议与方向 | 数据权威 | 权限/人工门禁 | 幂等/版本要求 | MVP | 契约引用 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GAT-01 | 生成或恢复候选名单确认预览：候选、关键证据、待核实项、邀请渠道/模板/期限和外部影响 | HTTPS REST，嵌入端 -> 平台（入/出） | 平台规范化预览权威；候选和简历原始事实由来源系统权威 | 招聘 HR；只能选择同一任务、同一匹配运行中的授权候选；预览不产生外部副作用 | `Idempotency-Key + taskVersion`；返回短期 `previewRef/inputHash/expiresAt`；过期后仍可读取审计但不可审批 | P0 | `[OAS] createCandidateListPreview/getCandidateListPreview`；[DOMAIN §10.2](../architecture/domain-model.md#102-humancheckpoint) |
| GAT-02 | 以未过期预览和服务端哈希创建或恢复 G4 待确认 checkpoint | HTTPS REST，嵌入端 -> 平台（入/出） | 平台 checkpoint 权威 | 招聘负责人/用人经理；确认前完整待确认正文必须与按钮同页可见 | `Idempotency-Key + If-Match`；checkpoint 绑定 `previewRef/inputHash`；同一预览重复请求返回已有门禁 | P0 | `[OAS] requestCandidateListReview/listHumanCheckpoints/getHumanCheckpoint` |
| GAT-03 | 批准、退回或取消候选名单 checkpoint 并填写意见 | HTTPS REST，确认人 -> 平台（入） | 平台不可变 checkpoint 决策权威 | 招聘负责人/用人经理；禁止智能体代签；被退回后须生成新预览和门禁 | `Idempotency-Key + If-Match`；同一 checkpoint 只允许一次终态决策 | P0 | `[OAS] decideHumanCheckpoint` |
| GAT-04 | 依据已批准 G4 checkpoint 冻结名单并查询当前版本 | HTTPS REST，嵌入端 -> 平台（入/出） | 平台确认名单版本权威 | checkpoint 必须为当前任务、类型正确、已批准，且 `inputHash` 与 `previewRef` 一致；只有该精确名单版本可用于面试批次 | 冻结使用 `Idempotency-Key + If-Match`；名单版本不可变，记录 preview/checkpoint/邀请方案快照 | P0 | `[OAS] confirmCandidateList/getCurrentCandidateList/listTaskCandidates/getHumanCheckpoint`；`[AAS] domainEventsV1(CandidateListConfirmed.v1)` |
| GAT-05 | 失效尚未执行的确认或创建变更单 | HTTPS REST，确认人 -> 平台（入） | 平台 | 仅在尚未产生不可逆外部副作用时允许；已邀请者进入补偿流程 | `Idempotency-Key + If-Match`；不删除历史确认；生成失效/补偿事件 | P1 | `[OAS gap] invalidateHumanCheckpoint` |
| GAT-06 | 读取并下载与名单绑定的版本化推荐报告 | HTTPS REST，嵌入端 -> 平台（出） | 平台推荐报告版本权威；来源证据和系统判断保持分栏 | 具备任务数据权限的招聘 HR/负责人；下载不得扩展调用人的字段权限 | 报告绑定任务、岗位方案、评分卡、匹配运行和名单版本；内容哈希稳定；TXT/JSON 为同一版本的不同表示 | P0 | `[OAS] getCurrentRecommendationReport/getRecommendationReport/downloadRecommendationReport` |

## 7. 面试编排

| ID | 输入/输出 | 协议与方向 | 数据权威 | 权限/人工门禁 | 幂等/版本要求 | MVP | 契约引用 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| INT-01 | 获取可用面试模板、渠道能力、限额和已知限制 | HTTPS REST，平台 -> 面试/消息连接器（出/入） | 供应商模板和能力权威 | 招聘 HR 可读；按租户配置过滤 | 连接器版本 + 模板 externalVersion；缓存需标记 observedAt | P0 | `[INT] GET /interview-templates`；`GET /connectors/{id}/capabilities` |
| INT-02 | 创建面试批次草案：已确认名单、模板、截止时间和通知策略 | HTTPS REST，嵌入端 -> 平台（入） | 平台编排事实权威 | 招聘 HR；必须引用有效 `CandidateListConfirmed` checkpoint | `Idempotency-Key + taskVersion`；checkpoint + 模板版本 + 候选集合哈希 | P0 | `[OAS] createInterviewBatch`；[DOMAIN §8.1](../architecture/domain-model.md#81-interviewbatch-与-interview) |
| INT-03 | 预览邀请内容、收件人、模板变量和风险项 | HTTPS REST，嵌入端 -> 平台（出） | 平台渲染预览；联系方式由 ATS/人才库权威 | 联系方式按需解密并掩码；发送前必须可人工检查 | 预览绑定批次 `version`、模板版本和收件人快照；不产生外部副作用 | P0 | `[OAS] createInterviewBatchPreview` |
| INT-04 | 人工确认并发送面试邀请 | HTTPS REST -> AMQP/连接器，嵌入端 -> 平台 -> 外部系统（出） | 面试供应商为邀请交付事实；平台为编排事实 | 明确发送门禁；需 `INTERVIEW_CREATE` 和已批准名单；记录操作者 | HTTP 幂等；`tenant + connector + CREATE_INTERVIEW + taskCandidate + templateVersion` 业务唯一 | P0 | `[OAS] sendInterviewBatch`；`[INT] POST /interviews`；`[AAS] connectorResultsV1/notificationCommandsV1` |
| INT-05 | 查询批次与单人邀请状态、失败原因和可重试动作 | HTTPS REST，嵌入端 -> 平台（出） | 平台编排状态；供应商投影为交付事实 | 任务可见范围；错误脱敏 | 返回资源版本、外部版本、observedAt；终态不可倒退 | P0 | `[OAS] getInterviewBatch/getInterview` |
| INT-06 | 提醒、延期、取消或重发 | HTTPS REST -> 连接器，嵌入端 -> 平台 -> 外部（出） | 供应商交付事实；平台命令记录权威 | HR；能力矩阵决定是否可用；取消/重发显示影响并人工确认 | `Idempotency-Key + If-Match`；每个副作用独立业务键；未知结果先查后重试 | P1 | `[INT] reminders/deadline/cancel`；`[AAS] connectorResultsV1/notificationResultsV1` |
| INT-07 | 接收邀请、开始、完成、过期、拒绝等供应商 Webhook | HTTPS Webhook，供应商 -> 平台（入） | 供应商交付状态权威 | HMAC/mTLS、租户和连接器校验；先落原始回执 | `sourceEventId` 去重；按 providerVersion/occurredAt 防乱序和状态倒退 | P0 | `[INT] interview.* webhook`；`[AAS] integrationNormalizedV1` |
| INT-08 | 拉取面试结果、评分、媒体和转写引用 | HTTPS REST，平台 -> 连接器（入） | 供应商原始结果权威；平台保留不可变结果版本 | `INTERVIEW_RESULT_READ`；媒体/转写按敏感数据授权，不进普通日志 | external interview + resultVersion 唯一；正文以对象引用 + hash 保存 | P0 | `[INT] GET /interviews/{id}/result`；[DOMAIN §8.2](../architecture/domain-model.md#82-interviewresultversion) |
| INT-09 | 对账与补偿：查询未知结果、重放失败项、人工关闭 | HTTPS REST/后台任务，平台 <-> 连接器 | 外部当前状态权威；平台补偿记录权威 | 集成运维；DLQ 重放需人工检查；不可盲目重发邀请 | reconciliation job 唯一键；保留 attempt、首次/最后时间和最终决议 | P1 | `[OAS gap] reconcileInterview/retrySyncJobItem`；[INT §10](./integration-contract.md#10-错误码与重试) |

## 8. 综合评价与 ATS 回流

| ID | 输入/输出 | 协议与方向 | 数据权威 | 权限/人工门禁 | 幂等/版本要求 | MVP | 契约引用 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| EVA-01 | 发起综合评价：匹配结果、面试结果、评分卡和证据版本 | HTTPS REST + AMQP，嵌入端 -> Core -> AI（入/出） | 平台 Evaluation 权威；各来源版本保持原权威 | 招聘 HR；只能使用有效且有权限的来源；缺失项必须显式标记 | `Idempotency-Key`；input snapshot/hash + workflow/model/result schema 版本 | P0 | `[OAS] createEvaluationRun`；`[AAS] aiCommandsV1(CONTENT_GENERATE)/aiResultsV1` |
| EVA-02 | 查询评价摘要、固定评分、风险、证据和来源覆盖率 | HTTPS REST，嵌入端 -> 平台（出） | 确定性总分/评级由平台规则权威；模型文本是派生内容 | 任务/候选权限；禁止输出无证据事实和敏感推断 | 返回 EvaluationVersion、规则/模型版本和每条证据引用 | P0 | `[OAS] getEvaluation`；[DOMAIN §8.3](../architecture/domain-model.md#83-evaluation) |
| EVA-03 | HR/用人经理补充结论、修正说明或标记需复核 | HTTPS REST，嵌入端 -> 平台（入） | 人工结论由平台权威；原生成版本不可变 | 有评价权限的 HR/用人经理；必填修改原因；不允许改写源证据 | `Idempotency-Key + If-Match`；创建新 EvaluationVersion/Review | P0 | `[OAS] reviseEvaluation` |
| EVA-04 | 人工确认最终评价并锁定回流版本 | HTTPS REST，确认人 -> 平台（入） | 平台不可变 G5 确认记录权威 | 人工确认；智能体不得自行决定录用或 Offer；高风险结论二次确认 | `Idempotency-Key + If-Match`；确认绑定精确 EvaluationVersion、业务 decision、风险确认和内容哈希 | P0 | `[OAS] confirmEvaluation`；`[AAS] domainEventsV1(EvaluationApproved.v1)` |
| EVA-05 | 回写评价摘要、报告引用、推荐标签到 ATS | HTTPS REST/连接器，平台 -> ATS（出） | ATS Application/候选记录为外部投影权威；平台评价权威 | 必须有批准 checkpoint 和写回 capability；字段白名单/脱敏 | 业务键含 application + evaluationVersion + templateVersion；外部版本冲突转人工 | P0 | `[INT] POST /applications/{id}/match-results`；`[AAS] connectorResultsV1` |
| EVA-06 | 回写 ATS 候选阶段或交还客户既有录用流程 | HTTPS REST/连接器，平台 -> ATS（出） | ATS Application 阶段权威 | 仅授权人员和显式门禁；录用、Offer 始终在客户流程决策 | `Idempotency-Key + expected externalVersion`；未知状态不猜测映射 | P1 | `[INT] PATCH /applications/{id}/stage` |

## 9. 连接器与客户系统适配

| ID | 输入/输出 | 协议与方向 | 数据权威 | 权限/人工门禁 | 幂等/版本要求 | MVP | 契约引用 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CON-01 | 创建/更新/启停连接器配置、认证引用和网络参数 | HTTPS REST，平台管理员 -> 平台（入） | 平台配置权威；密钥由 Secret Manager 权威 | 租户系统管理员；前端永不读取明文凭据；启停需审计 | `Idempotency-Key + If-Match`；配置版本化；密钥轮换独立版本 | P0 | `[OAS] createConnector/getConnector/updateConnector`；[INT §8](./integration-contract.md#8-认证与授权) |
| CON-02 | 查询连接器、支持版本、限额、数据范围和能力矩阵 | HTTPS REST，嵌入端/平台 -> 平台/连接器（出/入） | 连接器声明权威；平台计算 effective capability | 管理员看全量；HR 只看业务可用能力和限制 | capability snapshot/version + observedAt；过期能力不允许高风险动作 | P0 | `[INT] GET /connectors/{id}/capabilities`；`[OAS] listConnectors/getConnectorCapabilities` |
| CON-03 | 执行连通性、凭据权限和最小能力健康检查 | HTTPS REST，管理员 -> 平台 -> 连接器（双向） | 平台检查结果权威；下游响应是证据 | 系统管理员；结果脱敏；不得在健康检查执行真实业务副作用 | `Idempotency-Key`；healthCheckId；记录连接器配置版本 | P0 | `[INT] POST /connectors/{id}/health-checks`；`[OAS] createConnectorHealthCheck` |
| CON-04 | 维护字段、枚举、组织、岗位和状态映射 | HTTPS REST，实施/管理员 -> 平台（入/出） | 平台映射版本权威；外部字段定义由客户系统权威 | 实施管理员；发布映射前须校验和人工审批；未知枚举进入异常队列 | `Idempotency-Key + If-Match`；映射发布版不可原地修改 | P0 | `[OAS] getConnectorMapping/updateConnectorMapping` |
| CON-05 | 发起全量/增量同步并查询进度、游标、失败摘要 | HTTPS REST，管理员/调度器 -> 平台（入/出） | 平台 SyncJob 权威；源业务数据仍由外部系统权威 | 集成运维；全量同步需范围和速率门禁 | `Idempotency-Key`；connector + jobType + range/cursor 业务键；游标稳定 | P0 | `[INT] POST /connectors/{id}/sync-jobs, GET /sync-jobs/{id}`；`[OAS] createConnectorSyncJob/getConnectorSyncJob` |
| CON-06 | 重试单个失败项、重放 DLQ、标记已解决 | HTTPS REST，集成运维 -> 平台（入） | 平台运维决议权威 | 授权运维人工检查后重放；权限、映射、校验错误不可自动重试 | 新 attempt ID，保留原 message/business key；成功副作用不得重复 | P1 | `[OAS gap] retrySyncJobItem/replayDeadLetter`；`[AAS] x-smartai-topology` |
| CON-07 | 受控 RPA 兜底输入/截图/结果 | 桌面自动化 + 审计接口，平台 <-> 客户系统 | 客户页面事实权威；平台执行记录权威 | 仅书面授权、最小权限、低频试点；关键页面变化立即停机转人工 | 每个写动作沿用业务唯一键；执行前后查询状态；截图哈希和步骤版本 | P2 | [INT §13](./integration-contract.md#13-rpa-兜底原则) |

## 10. Webhook 与异步事件

| ID | 输入/输出 | 协议与方向 | 数据权威 | 权限/人工门禁 | 幂等/版本要求 | MVP | 契约引用 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| EVT-01 | 创建、查询、轮换密钥和停用平台出站 Webhook 订阅 | HTTPS REST，客户管理员 <-> 平台 | 平台订阅配置权威 | 租户系统管理员；目标 URL allowlist/SSRF 检查；密钥只显示一次 | `Idempotency-Key + If-Match`；订阅和签名密钥版本化 | P1 | `[INT] /webhook-subscriptions`；`[OAS gap] manageWebhookSubscription` |
| EVT-02 | 接收客户系统 Webhook 原始事件 | HTTPS Webhook，外部系统 -> 平台（入） | 原事件由来源系统权威；原始回执由平台权威 | HMAC-SHA256/mTLS、时间窗、租户、连接器和 payload 限制 | `tenant + connector + sourceEventId` 唯一；响应 2xx 后异步处理 | P0 | [INT §7](./integration-contract.md#7-webhook) |
| EVT-03 | 将原始 Webhook 归一化为平台集成事件 | AMQP，连接器 -> Core（入） | 规范化投影由 integration 模块权威 | 反腐层验证字段映射；供应商枚举不得直接进入核心聚合 | `messageId/sourceEventId/resourceVersion/schemaVersion`；Inbox 去重 | P0 | `[AAS] integrationNormalizedV1 / NormalizedWebhookEvent` |
| EVT-04 | 发布招聘、匹配、门禁、面试、评价、知识领域事实 | AMQP，Core -> 内部消费者（出） | 已提交业务事务的领域聚合权威 | 仅聚合事务 Outbox 发布；不得发布未提交或未来式“命令事件” | `messageId/businessKey/aggregateVersion/schemaVersion`；至少一次投递 | P0 | `[AAS] domainEventsV1 / DomainEvent` |
| EVT-05 | 向客户订阅者投递脱敏平台事件并接收回执 | HTTPS Webhook，平台 -> 客户（出） | 平台事件权威 | 订阅事件 allowlist；载荷只含引用，不含简历/转写/联系人 | eventId 去重；签名时间戳；指数退避、最大次数和客户侧 2xx ACK | P1 | `[INT] 标准事件与签名`；`[AAS] DomainEventEnvelope` |
| EVT-06 | 进入重试队列、DLQ、告警和人工重放 | AMQP + 管理 API，平台内部（双向） | Broker 投递事实与平台 Inbox/Outbox 权威 | 只有暂时错误自动重试；确定性错误直接 DLQ；重放需授权人工 | 重试不修改正文/messageId/businessKey；只增加 attempt/x-death | P0 | `[AAS] x-smartai-topology/x-smartai-idempotency` |

## 11. AI 命令、结果与运行状态

| ID | 输入/输出 | 协议与方向 | 数据权威 | 权限/人工门禁 | 幂等/版本要求 | MVP | 契约引用 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| AIR-01 | 由业务命令创建 AgentRun，并查询步骤、当前状态和等待人工项 | 业务 REST/内部命令创建，HTTPS REST 查询（入/出） | 平台运行编排事实权威 | 发起业务命令需对应权限；同一任务最多一个活动 PRIMARY run | 业务命令负责 HTTP 幂等；AgentRun/Step 版本；活动主运行数据库唯一约束 | P0 | `[OAS] getAgentRun`；[DOMAIN §10.1](../architecture/domain-model.md#101-agentrunagentstep-与-modelinvocation) |
| AIR-02 | 下发文档解析、知识索引、人才召回、证据提取、内容生成命令 | AMQP，Java Core -> Python dispatcher（出） | Core 的业务意图权威 | Core 先校验权限、状态与数据范围；消息只带加密 `payloadRef` | `messageId/businessKey/idempotencyKey/inputHash/workflowVersion/resultSchemaRef` | P0 | `[AAS] aiCommandsV1 / AICommand` |
| AIR-03 | dispatcher 转为 Celery 私有任务并回报执行结果 | Celery 私有协议 + AMQP，Python -> Core（入） | AI 运行指标/结构化输出由 AI 服务产生；Core 验证后落库 | dispatcher Inbox 成功并创建稳定 task ID 后才 ACK；结果 Schema 必须通过 | Celery task ID=`tenantId+businessKey`；结果 `causationId` 指向命令 `messageId` | P0 | `[AAS] aiResultsV1 / AIResult`；`x-smartai-idempotency` |
| AIR-04 | 读取受控输入对象并写入结果对象 | 加密对象存储，AI 服务 <-> 平台 | 输入业务版本由 Core 权威；结果对象由 AI 服务写、Core 验收 | 校验 tenant、ACL、sha256、大小、分类、schemaRef 和有效期 | opaque `object://` 引用；内容哈希；对象不可变；预签名地址不入消息 | P0 | `[AAS] PayloadRef / x-smartai-sensitive-data-policy` |
| AIR-05 | 取消可取消的 AgentRun 或从可重试步骤恢复 | HTTPS REST + AMQP，用户/Core -> AI（入） | Core 状态机权威 | 具备任务管理权限；不可撤销已发生的外部副作用；等待人工时不超时自动批准 | `Idempotency-Key + If-Match`；cancelRequested 单调；恢复沿用业务键并新增 attempt | P1 | `[OAS gap] cancelAgentRun/retryAgentRun`；[DOMAIN §10.1](../architecture/domain-model.md#101-agentrunagentstep-与-modelinvocation) |
| AIR-06 | 记录模型、提示模板、输入/输出哈希、成本、时延和策略判定 | 平台内部审计/指标（出） | 平台 ModelInvocation 记录权威 | 运维可看脱敏指标；提示和正文仅授权审计通过对象引用访问 | 每次 invocation 唯一；关联 AgentStep、模型修订和 promptVersion | P0 | `[DOMAIN] ModelInvocation`；`[AAS] ExecutionMetrics` |

## 12. 受控文件交换

| ID | 输入/输出 | 协议与方向 | 数据权威 | 权限/人工门禁 | 幂等/版本要求 | MVP | 契约引用 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| FIL-01 | 客户提交组织、岗位、候选、简历或历史 JD 批次清单 | SFTP/客户认可对象存储，客户 -> 平台（入） | 文件内业务事实由客户来源系统权威 | 仅在 API 不可用或批量初始化时启用；目录、字段和用途白名单 | `manifestId + fileSha256 + recordBusinessKey + sourceVersion` 去重；UTF-8/ISO 8601 | P1 | [INT §4.1](./integration-contract.md#41-基础规则)；`[OAS gap] createFileImport` |
| FIL-02 | 上传加密数据文件和附件，平台返回接收回执 | SFTP/对象存储 + HTTPS receipt，客户 -> 平台（入/出） | 原文件由客户权威；接收状态由平台权威 | 恶意文件扫描、大小/MIME/行数限制、租户目录隔离；附件最小访问 | 临时文件完成后原子改名；校验 sha256/size；同 manifest 重复返回首次回执 | P1 | `[OAS gap] completeFileImport/getFileImport` |
| FIL-03 | 返回逐条成功/失败、映射错误和可重试结果文件 | SFTP/对象存储，平台 -> 客户（出） | 平台处理结果权威 | 错误文件脱敏；仅原提交租户/服务身份可下载 | 结果关联 manifest/version；每条保留 source row key，不因部分失败重放成功项 | P1 | `[OAS gap] getFileImportErrors`；[INT §9-10](./integration-contract.md#9-幂等与并发) |
| FIL-04 | 导出经授权的岗位方案、名单或评价摘要 | HTTPS 异步导出 + 对象存储，用户 -> 平台 -> 用户（出） | 平台业务版本权威 | 导出需业务权限、目的和审计；候选 PII 按策略脱敏；链接短期单次 | `Idempotency-Key + exportSpecHash + sourceVersions`；文件 hash 与过期时间 | P2 | `[OAS gap] createExport/getExport`；[INT §11](./integration-contract.md#11-数据安全与脱敏) |
| FIL-05 | 归档、保留到期和删除传播状态 | 后台任务 + 管理 API，平台内部/外部（双向） | 平台保留策略权威；外部删除结果由来源系统权威 | 数据管理员；法律保留优先；删除失败告警并人工处理 | retentionPolicyVersion；tombstone/event ID；跨索引/缓存/对象存储可对账 | P1 | [DOMAIN §16](../architecture/domain-model.md#16-数据生命周期)；`[OAS gap] getDataDispositionJob` |

## 13. 审计、追踪与合规输出

| ID | 输入/输出 | 协议与方向 | 数据权威 | 权限/人工门禁 | 幂等/版本要求 | MVP | 契约引用 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| AUD-01 | 写入登录、读取、搜索、导出、解密、模型调用、人工修改、门禁和外部写入事件 | Outbox/AMQP -> audit 模块（入） | 审计模块不可变事实权威 | 业务模块只能追加审计事件，不能修改/删除；敏感正文只存加密引用 | `eventId` 唯一；关联 request/correlation/causation/actor/resource/version | P0 | [DOMAIN §12](../architecture/domain-model.md#12-审计上下文)；`[AAS] domainEventsV1` |
| AUD-02 | 按时间、操作者、任务、候选、动作和 trace 查询审计记录 | HTTPS REST，审计人员 -> 平台（出） | 审计模块 | 审计员/安全管理员；候选敏感字段继续按范围控制；所有查询本身也审计 | 稳定游标；事件不可变；结果包含记录时间与业务发生时间 | P0 | `[OAS] listAuditEvents/getAuditEvent` |
| AUD-03 | 查看一次智能体运行的输入版本、步骤、模型、规则、证据、门禁和外部副作用链路 | HTTPS REST，授权用户 -> 平台（出） | 各领域事实 + 审计关联权威 | 任务用户看业务解释；审计员看脱敏技术链路；不暴露密钥/完整 prompt | `traceId/correlationId/causationId` 关联；显示精确版本和内容哈希 | P0 | `[OAS] getAuditTrace`；`[AAS] BaseEnvelope` |
| AUD-04 | 导出审计报告或监管留档包 | HTTPS 异步导出，审计员 -> 平台（入/出） | 审计模块 | 审计员双重确认；记录目的、范围和下载者；加密并设置短期访问 | `Idempotency-Key + queryHash + snapshotAt`；报告签名/hash/version | P1 | `[OAS gap] createAuditExport/getAuditExport` |
| AUD-05 | 查询幂等记录、业务操作、Webhook 回执、Inbox/Outbox 和 DLQ 处理状态 | HTTPS 管理 API，集成运维 -> 平台（出） | 对应基础设施存储权威 | 运维/审计角色；载荷默认不展开；重放另需人工授权 | 返回原始 key、attempt、版本和状态；查询不改变投递状态 | P1 | `[OAS gap] getIntegrationTrace`；`[AAS] x-smartai-topology/x-smartai-idempotency` |
| AUD-06 | 对冲突、失败或人工覆盖形成处置意见并关闭告警 | HTTPS REST，授权运维/业务负责人 -> 平台（入） | 平台处置记录权威 | 职责分离；业务结论由业务负责人，技术重放由运维；必填原因 | `Idempotency-Key + If-Match`；追加 decision，不改历史错误/事件 | P1 | `[OAS gap] resolveOperationalIssue` |

## 14. MVP 最小闭环与契约完成度

首个客户可上线试点至少要同时满足下列接口链路，不能只完成可视化页面：

1. `EMB-01` 至 `EMB-08`：ATS 内一次进入正确岗位/候选上下文，无二次登录，可安全刷新会话并返回宿主。
2. `REC-01` 至 `REC-12`：自然语言需求形成可编辑草案，人工确认后生成版本化岗位方案，完成独立 G3 门禁后受控发布。
3. `KNO-01` 至 `KNO-06`、`KNO-08`：历史 JD、用人标准和人才画像可维护、审核、发布、检索并提供引用。
4. `MAT-01` 至 `MAT-07`：在授权简历库内检索，固定规则评分，每个结论可回溯到简历和评分卡版本。
5. `GAT-01` 至 `GAT-04`：待确认内容在确认前完整可见，未经确认不能创建面试邀请。
6. `INT-01` 至 `INT-05`、`INT-07`、`INT-08`：在线面试邀请、状态、结果和转写引用闭环，重复/乱序事件不产生重复副作用或状态倒退。
7. `EVA-01` 至 `EVA-05`：综合评价可解释、可人工修订和批准，并将批准版本回写 ATS。
8. `CON-01` 至 `CON-05`、`EVT-02` 至 `EVT-06`、`AIR-01` 至 `AIR-04`、`AIR-06`、`AUD-01` 至 `AUD-03`：能力协商、同步、消息、AI 跨运行时和审计具备生产基线。

当前机器契约应至少覆盖全部 `P0` 平台 REST API、嵌入 API、核心异步信封和路由。仍仅在 `[EMBED]` 或 `[INT]` 中以文字定义的客户侧供应商接口，由每个客户连接器在实施阶段交付 provider/consumer contract、字段映射、状态映射、样例载荷和回放测试；不允许在核心代码中硬编码某一家 ATS 的字段或状态。
