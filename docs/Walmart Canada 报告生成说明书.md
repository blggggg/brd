# Walmart Canada 报告生成说明书

## 1. 目的与适用范围

本说明整理 Walmart Canada（下称 WMCA）报告的任务调用顺序、测试数据加载与缓存、测试协议表生成规则，以及无数据或映射缺失时的行为。WMCA 专属分支只在 `objRTW.isWalmartCanada()` 为 `true` 时运行；其他客户的报告流程应保持原样。

规则依据：BRD v1.2 第 11 页 b 项。

## 2. 测试协议段落

### 2.1 触发时机

`ReportEngineTaskController.executeTask()` 在将报告保存为 Word 或 PDF 前，按顺序执行以下代码内任务：

1. `ReportCoverWalmartCanada`
2. `DrawProtocolSummaryTable`
3. `FailureSummaryWalmartCanada`

这三个任务由 WMCA 代码分支追加，不配置在数据库任务清单中。`DrawProtocolSummaryTable.executeTask()` 调用 `drawWalmartCanadaProtocols()`，并将协议内容写入模板书签 `TEST_PROTOCOL`。

### 2.2 数据与排序

协议数据来自 `MiscellaneousMapper.getProtocols`。结果以 `PROTOCOL_NO` 和 `PROTOCOL_DOC_NAME` 为单位，每行的 `ASSIGN_ORDER` 为对应记录 `MIN(ORDER_NO)`。

输出按以下两个键依次降序排列：

1. Protocol#：`NTR.TD_PROTOCOL_MASTER.PROTOCOL_NO`，降序。
2. 同一 Protocol# 内的分配顺序：`BVRPTDAT2.SAMPLE_PRODUCT_PROTOCOL.ORDER_NO`，降序。

`TD_PROTOCOL_MASTER` 没有用于此排序的 `ORDER_NO` 列，不要以该表作为次排序依据。

示例（52262240103）：`12300 → 10000 → CPSD 7601 → WMCA 7601 → 1010`。

## 3. WMCA 测试数据、缓存和评分

### 3.1 查询与缓存范围

测试数据按需加载，不在应用启动时预加载，也不是 Ehcache 缓存。一次报告请求中，第一次有 WMCA 任务需要数据时，`ReportTaskGeneric.getWalmartCanadaTests()` 检查 `objRTW.getWalmartCanadaTests()`：

- 已有值：直接复用。
- 值为 `null`：调用 `sampleTestService.getSampleTestListForWalmartCanada(masterActsNo, companyId)` 查询，并将不可修改的结果列表保存到当前 `ReportTaskWrapper`。

因此，同一报告中的 `OverAllRatingInfo` 和 `FailureSummaryWalmartCanada` 共用一次查询；新报告请求会重新查询，不复用上一次报告的数据。

### 3.2 查询过滤和关联

`SampleTestMapper.getSampleTestListForWalmartCanada` 按主样品和公司筛选，只保留 `NA_TYPE` 为空、`NT` 或 `NR` 的测试。

- `TEST_PROPERTY`、`RATING` 是内连接；没有评级记录的测试不会进入结果。
- fail code 和 protocol 是左连接；对应值可能为空。

### 3.3 访问保护

`setWalmartCanadaTests()` 只允许在 WMCA 报告中调用，并拒绝 `null` 列表；违反条件时抛出异常。

## 4. 无数据和映射缺失时的行为

| 情况 | Overall Rating | Failure Summary |
|---|---|---|
| 没有测试 | 类别评级使用 `noTestRating` 参数 | 两个相关区段显示 `N/A` |
| 主样品没有 overall rating | Overall 和 corrective-action 字段留空并记录 warning | — |
| 测试缺少 fail code 或 rating，或 rating 不在 `ratingPriority` | 类别评级和 corrective action 留空并记录 warning | — |
| FAIL 测试缺少 `ST_ID`、名称、测试行或 fail code | — | 跳过该测试并记录 warning |
| 没有 FAIL 测试 | — | 显示 `N/A` |
| 同一测试行存在互相冲突的 Compliance / Quality fail code | — | 抛出 `IllegalStateException` |
| `ratingPriority` 未将 FAIL 放在首位 | 抛出 `IllegalArgumentException` | — |

缺失映射时应按表中规则留空、跳过或告警；冲突映射和非法优先级配置属于硬错误，应继续抛出异常，避免生成含糊报告。

## 5. WMCA 报告整体调用链

### 5.1 开关与任务控制

- `MISC_SETUP` 的 property 267 配置 `PROPERTY_DESC = 'WALMART_CANADA'`，用于打开 Walmart group 标志和 `isWalmartCanada` 分支。
- `ACTSLAB.NEW_ARG_TASK_CONTROL` 为公司 2105259 配置 `LoadDataSource`，报告格式 ID 为 60；通过存储过程 `SP_NEW_ARG_TASK_CONTROL` 读取。
- `ACTSLAB.NEW_ARG_SYSTEM_QUERY` 保存命名 SQL，例如 `NEWARG_REPORT_TASKS_BY_FORMAT_ID` 和 failure-reason 查询。
- `ACTSLAB.NEW_ARG_REPORT_TASK_SETUP` 保存格式 60 的任务顺序、参数和公司配置。
- `ACTSLAB.NEW_ARG_REPORT_TASK` 将 task id 映射到类/Bean 名称 `REPORT_TASK_OBJECT`。

### 5.2 格式 60 任务顺序

`ReportEngineTaskController.executeTask()` 根据任务顺序循环执行 `ctx.getBean(taskName)`：

| Order No | Task |
|---:|---|
| 10 | `ActivateAsposeLicense` |
| 20 | `NewDocumentWithTemplate`（WMCA 模板） |
| 30 | `HeaderFooterReplacement` |
| 40 | `OverAllRatingInfo` |
| 50 | `ReportCoverWithSampleImage` |
| 60 | `ReportCoverSecond` |
| 70 | `SignatureBlock` |
| 80 | `ReportCoverThird` |
| 90 | `ProcessTestResultTableTasks` |
| 94 | `AppendColorfastnessDoc` |
| 95 | `AdditionalDocRetest` |
| 100 | `FailureImage` |
| 110 | `AdditionalDocWalmart` |
| 120 | `SaveDocumentToMSWord` |

Word/PDF 保存前的 WMCA 代码内追加任务顺序见第 2.1 节。`ReportCoverWalmartCanada`、`DrawProtocolSummaryTable` 和 `FailureSummaryWalmartCanada` 不在数据库任务清单中。

### 5.3 相关业务数据表与模板

报告数据涉及 `MASTER_SAMPLE`、`SAMPLE`、`SAMPLE_TEST`、`TEST_PROPERTY`、`RATING`、`SAMPLE_TEST_PROTOCOL`、`SAMPLE_PRODUCT_PROTOCOL`、`NTR.TD_PROTOCOL_MASTER` 和 `NTR.MP_TEST_LINE_FAIL_CODE`。

模板为 `GENERIC_NEWARG_WMCA.dot`，由 `NewDocumentWithTemplate` 任务的参数指定。测试协议段落写入 `TEST_PROTOCOL` 书签。

## 6. 实施核对项

1. 只有 WMCA 报告进入 Walmart Canada 专属分支；其他客户的输出不变。
2. 协议排序使用 `PROTOCOL_NO DESC`，再使用 `SAMPLE_PRODUCT_PROTOCOL.ORDER_NO DESC`。
3. 协议摘要写入 `TEST_PROTOCOL` 书签，并在保存 Word/PDF 前生成。
4. WMCA 测试列表按单次报告请求惰性查询并共享，不跨报告缓存。
5. 数据缺失按第 4 节处理；映射冲突和非法 `ratingPriority` 保留硬错误。
6. 数据库任务清单仅负责格式 60 的配置任务；三个保存前的 WMCA 专属任务由代码按固定顺序追加。

## 7. 当前 BRD 项目代码范围

本说明依据用户提供的技术资料编写。当前检查到的 `E:\project\brd-management` 是文档版本管理应用，源码中没有 `ReportEngineTaskController`、`DrawProtocolSummaryTable`、`MiscellaneousMapper` 或上述报告生成模块。因此，本说明定义了目标行为和核对标准，但不代表这些 WMCA 报告链路已在该仓库中实现。要直接修改报告生成逻辑，需要将相应报告引擎源码纳入该项目或提供其仓库路径。

## 8. 表设计

### 8.1 当前 BRD 中已部署的任务配置表

当前 BRD 新增的 `BVLDB.task` 是按公司维护执行类和顺序的配置表。字段如下：

| 字段 | 类型 | 规则/用途 |
|---|---|---|
| `id` | BIGINT | 主键，自增 |
| `company_id` | BIGINT | 公司 ID，必填 |
| `report_format_id` | INT | 报告格式 ID，必填；0 表示通用格式，WMCA 示例格式为 60 |
| `class_name` | VARCHAR(255) | Spring Bean / 执行类名，必填 |
| `order_no` | INT | 执行顺序，必填，默认 0；值越小越先执行 |
| `enabled` | TINYINT(1) | 是否启用，默认 1 |
| `other_fields` | JSON | 扩展参数对象，必填，默认由接口写入 `{}` |
| `remark` | VARCHAR(500) | 备注，可空内容（存为空字符串） |
| `created_at` | TIMESTAMP | 创建时间 |
| `updated_at` | TIMESTAMP | 更新时间，更新记录时自动刷新 |

主键为 `id`；索引 `idx_task_company_format_order(company_id, report_format_id, order_no, id)` 支持按公司、格式和顺序读取。读取排序为 `order_no ASC, id ASC`，相同顺序号时用 `id` 保证结果稳定。管理页面和 MyBatis Mapper 按 `companyId + reportFormatId` 查询，并支持增删改查。

### 8.2 报告引擎的目标关系模型

贴文只给出了旧库表名及少量配置事实，没有提供它们的完整 DDL。下表区分已知职责和需要按实际数据库结构确认的键：

| 表 | 已知职责 | 关系/核对点 |
|---|---|---|
| `ACTSLAB.NEW_ARG_TASK_CONTROL` | 公司/报告格式对应的入口控制；WMCA 样例为公司 2105259、`LoadDataSource`、格式 60 | 由 `SP_NEW_ARG_TASK_CONTROL` 读取；完整主键和列名需查实际 DDL |
| `ACTSLAB.NEW_ARG_REPORT_TASK_SETUP` | 某公司某格式的任务顺序和参数配置 | 至少能识别格式、公司、任务及顺序；按 `ORDER_NO` 排序 |
| `ACTSLAB.NEW_ARG_REPORT_TASK` | 将 task id 映射到 `REPORT_TASK_OBJECT` 类/Bean 名称 | setup 行通过 task id 找到执行对象 |
| `ACTSLAB.NEW_ARG_SYSTEM_QUERY` | 按名称保存 SQL 查询 | 由查询名称引用；不要把 SQL 直接拼入任务顺序表 |
| `MISC_SETUP` | property 267 的 `WALMART_CANADA` 标志 | 控制 WMCA 专属分支 |

建议将关系理解为：

```text
公司 + 报告格式
        │
        ├── NEW_ARG_TASK_CONTROL ── LoadDataSource 控制入口
        │
        └── NEW_ARG_REPORT_TASK_SETUP (ORDER_NO, 参数, task id)
                       │
                       └── NEW_ARG_REPORT_TASK (task id → REPORT_TASK_OBJECT)

NEW_ARG_SYSTEM_QUERY (query name → SQL) ── 被数据加载/任务参数引用
MISC_SETUP property 267 ── 决定是否执行 WMCA 专属分支
```

BRD 的 `task` 表已经增加报告格式维度，至少采用以下逻辑键：

```text
(company_id, report_format_id, order_no, task_id)
```

当前 `task` 表把类名保存在 `class_name`，并把各任务的扩展参数放在 `other_fields` JSON 中，适合该 BRD 管理界面直接维护。若进一步迁移完整的旧报告配置模型，可将“任务定义”与“公司格式任务配置”拆开：任务定义保存 `task_id`、`bean_name`、说明；公司格式配置保存 `company_id`、`report_format_id`、`task_id`、`order_no`、启用状态和 JSON 参数。WMCA 的固定保存前追加任务仍由代码分支执行，不插入该数据库配置清单，避免重复运行。

本次 DDL 文件：`backend/database/mysql/V2__task_report_format.sql`。应用本地 H2 模式另有 Flyway 迁移 `V4__task_report_format.sql`。线上 BVLDB 已先执行该 DDL，再更新接口代码。接口使用 MyBatis-Spring-Boot Starter 4.0.0（与当前 Spring Boot 4 兼容），Mapper 位于 `TaskMapper`。

在拿到旧库 DDL 前，不应假设 `NEW_ARG_*` 的真实列名、外键或唯一约束，也不应直接对这些旧表执行迁移。当前 `BVLDB.task` 的实际 DDL 以服务器上的表结构为准；上面的逻辑键是后续报告格式化设计建议，不是已部署字段。
