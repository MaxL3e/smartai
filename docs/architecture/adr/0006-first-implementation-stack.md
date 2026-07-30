# ADR-0006：首期统一使用 jOOQ 与 Celery + RabbitMQ

- 状态：接受
- 日期：2026-07-30
- 决策人：技术负责人

## 背景

系统架构已确定 Spring Boot 模块化核心、FastAPI AI 服务和 RabbitMQ，但数据访问与 Python 异步执行仍存在二选一。首期同时保留多套同类基础库会增加脚手架、监控、事务和故障处理成本。

## 决策

- Java 业务核心使用 jOOQ 访问 PostgreSQL，Flyway 管理迁移。
- Python AI 服务使用 Celery + RabbitMQ 处理解析、索引、检索和证据提取异步作业。
- Java 与 Python 之间使用平台 `AICommand/AIResult` 消息信封。Python dispatcher 负责把平台命令幂等转换为 Celery 私有任务，Worker 结果再转换回平台事件；Java 不直接生成 Celery task protocol。
- 平台投递和 Celery 计算分别拥有重试与死信队列，禁止两层同时重放外部副作用；Java 与 Python 不共享业务表写权限。

## 结果

jOOQ 提供显式 SQL 和编译期字段类型，适合复杂筛选和审计查询；Celery 提供成熟的 RabbitMQ worker、重试和运维生态。团队需要维护代码生成和 Celery 任务规范，但避免首期重复建设 worker 框架。

## 复审条件

客户数据库不兼容 jOOQ 支持范围；Celery 无法满足已测得的吞吐、取消或可观测要求；或团队已有经过生产验证且能满足同等约束的统一替代方案。
