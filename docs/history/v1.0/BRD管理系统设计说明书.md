# BRD 管理系统设计说明书

版本 1.0  
日期 2026 年 9 月 26 日  
适用对象 产品负责人、业务分析师、开发工程师、测试工程师和运维人员

## 1 建设目标与设计范围

BRD 指 Business Requirements Document，即业务需求文档。本系统将分散在 Word、邮件和聊天记录中的需求，统一管理为可编写、可评审、可批准、可追溯的项目资产。系统应明确回答：谁提出了需求，当前生效的是哪个版本，谁批准了这个版本，变更影响了哪些需求和验收项。

第一期采用企业内部单组织、多项目模式。所有业务资源必须属于项目；用户在不同项目中可承担不同角色。对外 SaaS 租户隔离不在第一期范围，不以简单增加 tenant_id 宣称已支持多租户。

核心设计是将“可编辑工作稿”“冻结版本”“审批实例”和“生效版本指针”分开保存。任何审批只作用于确定的冻结版本，发布后不能覆盖原文。后续修改创建新工作稿并重新审批。

本文件定义待实施的设计和验收要求，不表示系统已开发完成。容量、工期和性能指标是立项估算与验收目标，需在实施和压测时确认。

### 1.1 范围边界

| 范围 | 第一期交付 | 后续扩展 |
|---|---|---|
| 组织与访问 | 本地账号、项目成员、项目角色、停用账号 | 企业 SSO、组织目录同步、多租户 |
| 文档 | 模板、章节编辑、结构化需求、附件、冻结快照 | Word 内容导入、多人实时协同 |
| 协作 | 章节评论、待办、版本对比 | 在线会议、聊天集成 |
| 审批 | 串行节点、节点内全员会签、退回和撤回 | 条件分支、委托、复杂 BPMN |
| 发布 | 发布基线、变更申请、历史查询、归档 | 法律效力电子签名、公开发布 |
| 交付 | 带版本信息的 PDF 导出 | DOCX 导出、自定义企业版式 |
| 追踪 | 需求与验收条件、外部工单链接 | Jira 和 GitLab 双向同步、自动测试回写 |

不包含开发任务排期、完整缺陷管理、聊天平台或合同签署功能；外部工单在第一期只是经过校验的链接。

### 1.2 成功标准

用户可完整完成“创建项目 → 编写 BRD → 评审 → 审批 → 发布 → 发起变更 → 再次发布”。任意历史发布版本均可还原其正文、需求、附件清单和审批证据。跨项目读取和下载必须被后端拒绝，重复审批不能引发重复发布。

## 2 角色与权限

权限由平台角色、项目成员身份、项目角色、资源状态共同决定。前端隐藏按钮仅改善体验，后端对每次查询和操作独立鉴权。默认拒绝，无项目成员身份即无正文权限。

| 角色 | 主要权限 | 限制 |
|---|---|---|
| 平台管理员 | 用户、全局模板、系统设置、审计运维 | 不自动拥有业务正文访问权，不代替审批人批准 |
| 项目负责人 | 项目成员、审批策略、发布、归档 | 修改策略只影响新审批；发布必须满足审批条件 |
| BA 文档作者 | 创建、编辑授权工作稿、提交评审、发起变更 | 不可修改冻结版本，不批准自己提交的文档 |
| 评审人 | 查看被分配版本、评论、提出阻塞问题、完成评审 | 不直接编辑正文，不代替业务批准 |
| 审批人 | 对本人当前待办批准或退回 | 必须是有效成员；退回需理由；禁止自批 |
| 阅读者 | 查看项目已发布版本、授权导出 | 默认不可见工作稿和未发布附件 |

一个用户可以有多个项目角色，但禁止自批的规则优先。项目负责人若同时为作者，可发布已由其他人批准的版本，不能绕过他人审批。第一期至少需要作者和独立审批人两名有效成员。

管理员紧急访问业务内容必须由单独授权流程授予，带到期时间和原因；第一期不提供隐式超级用户正文接口。停用账号立即撤销会话。审批人停用时暂停实例，由负责人撤回并重新提交，保留旧审批记录。

## 3 业务功能与页面设计

### 3.1 信息架构

登录后进入工作台；左侧导航为工作台、项目、我的待办、我的文档、模板、审计和系统设置。项目内提供概览、BRD 列表、成员、审批设置四个入口。

BRD 详情采用左侧章节树、中间正文、右侧评论和待办布局。顶部固定显示文档编号、工作稿或版本号、状态、作者、最后保存时间。历史版本页面明确标识“只读”，不能出现会被误认为覆盖历史的保存按钮。

| 页面 | 核心内容 | 关键操作与异常反馈 |
|---|---|---|
| 工作台 | 我的草稿、待评审、待审批、近期发布 | 跳转有权限的文档；失去权限后不展示摘要 |
| 项目 BRD 列表 | 编号、标题、负责人、已发布版本、当前流程 | 按状态和作者筛选；分页；空状态引导创建 |
| BRD 编辑器 | 模板章节、需求条目、附件、保存状态 | 自动保存与手动保存；冲突时禁止静默覆盖 |
| 版本详情 | 冻结正文、需求快照、附件、评审意见 | 对比上一版本；查看审批证据；只读导出 |
| 审批工作台 | 版本摘要、变更内容、阻塞项、当前节点 | 批准或退回；失效待办提示刷新 |
| 变更申请 | 原因、受影响需求、风险、成本、目标日期 | 从已发布基线创建；关联新版本 |
| 模板管理 | 模板版本、章节顺序、必填规则 | 发布模板；已有 BRD 不随模板更新自动变化 |
| 审计查询 | 操作人、对象、动作、时间、结果 | 按项目过滤；导出受权限控制 |

### 3.2 BRD 模板及内容结构

默认模板包含背景与问题、目标与成功指标、利益相关方、范围及不在范围、现状与目标流程、业务需求、业务规则、数据需求、非功能需求、依赖与风险、验收标准。每章有稳定 section_id；移动和改名不改变其身份。

文档元数据包括编号、标题、所属项目、负责人、业务域、优先级、标签、期望交付日期和密级。编号示例为 BRD-PROJ-000123，由服务端分配且不可复用。

需求条目是结构化数据，不只是富文本段落。每条包含稳定需求编号、标题、描述、提出人、业务价值、优先级、所属章节、验收条件和关联需求。验收条件应能被测试验证，例如“无项目成员身份的用户访问正文时返回不可访问结果”。

第一期统一使用 P0、P1、P2 优先级。需求稳定 ID 跨版本保留；删除需求只代表新版本不再包含它，历史版本仍保留原始内容。版本对比必须识别新增、修改、删除和移动。

### 3.3 编辑与附件规则

保存接口携带工作稿 revision。后台约 10 秒一次防抖自动保存仅在内容变化时触发；显示保存中、已保存和保存失败。浏览器本地缓存按用户和文档隔离，退出时清理；服务端没有确认前不得显示已保存。

第一期不承诺多人实时合并。两个作者基于同一 revision 编辑时，先保存者成功，后保存者收到 409 冲突及最新 revision，界面保留本地内容供人工比较。

附件单文件上限建议 20 MB，仅允许 PDF、DOCX、XLSX、PNG、JPEG；同时校验扩展名、MIME 和文件签名。上传后先隔离并扫描，扫描通过才可预览、下载或被提交版本引用。归档包不在第一期允许范围。

附件使用私有对象存储；记录 SHA-256、大小、类型、上传人、扫描状态和对象键。对象键不可覆盖，下载先鉴权再生成不超过 5 分钟的临时链接。清理任务必须排除被任意历史版本引用的对象。

## 4 流程与状态机

### 4.1 主流程

工作稿 → 冻结版本并提交评审 → 评审通过 → 串行审批 → 全部批准 → 项目负责人发布。

评审不通过、审批退回或作者撤回时，冻结版本终结；系统复制其内容为新工作稿，后续提交生成新的版本序号。已批准或已发布的旧版本不被修改。

| 对象及当前状态 | 动作 | 目标状态 | 必须满足的条件 |
|---|---|---|---|
| 工作稿 EDITING | 提交 | 工作稿 SUBMITTED，版本 IN_REVIEW | revision 匹配、必填完整、附件通过扫描、参与人有效 |
| 版本 IN_REVIEW | 完成评审 | IN_APPROVAL | 所有评审人完成、阻塞意见全部关闭、无自批配置 |
| 版本 IN_REVIEW | 退回 | CHANGES_REQUESTED | 评审人给出理由；取消后续待办并创建新工作稿 |
| 版本 IN_APPROVAL | 节点批准 | IN_APPROVAL 或 APPROVED | 当前节点所有指定人员批准；非当前节点不得操作 |
| 版本 IN_APPROVAL | 退回 | CHANGES_REQUESTED | 当前审批人填写理由；剩余待办取消 |
| 版本 IN_REVIEW 或 IN_APPROVAL | 撤回 | WITHDRAWN | 作者或负责人发起；记录理由并取消待办 |
| 版本 APPROVED | 发布 | PUBLISHED | 负责人操作、内容哈希一致、变更仍基于当前生效版本 |
| 旧版本 PUBLISHED | 新版发布 | SUPERSEDED | 同一事务切换 current_published_version_id |
| 文档 ACTIVE | 归档 | ARCHIVED | 无进行中评审审批；所有版本保留并禁止新建工作稿 |

APPROVED 内容需要继续修改时，应以其为源创建新工作稿，并在发布前使原批准版本失效，防止发布已被编辑意图替代的内容。文档归档后仅负责人可恢复为 ACTIVE，恢复动作记录审计。

### 4.2 审批策略

第一期采用有序节点列表，例如“业务负责人 → 技术负责人 → 产品负责人”。每个节点可以分配多人，全员通过后才激活下一节点；任一人退回，实例终止。审批节点、审批人、顺序在提交时快照保存，管理员后续改策略不影响在途实例。

提交前检查有效审批人、禁止自批规则和流程节点完整性。评审未关闭的阻塞意见禁止进入审批。超时只提醒，不自动通过。审批操作基于任务 ID、操作人、期望状态和幂等键，重复点击返回第一次结果。

### 4.3 变更与发布

首次发布后，新的工作稿必须关联变更申请，包含变更原因、范围、受影响需求、业务和技术风险、预计成本、验收变化。第一期每个文档最多一个活动工作稿或在途版本，避免多个分支竞争发布。

发布事务锁定文档记录，验证当前已发布指针与变更基线相同，验证审批完成，再更新新旧版本状态、生效指针、变更申请状态、审计和 outbox 事件。任一步失败整体回滚。未审批的新内容不可借由“发布”接口进入基线。

版本采用递增整数序号，在界面显示 V1、V2、V3。退回或撤回版本占用序号且不重用。导出包含文档编号、版本号、状态、导出时间及内容校验摘要；未发布版本标记“未发布”。

## 5 系统架构与技术方案

### 5.1 架构

采用模块化单体，按业务边界组织模块：identity、project、brd、review、approval、change、attachment、audit、notification。前端通过同源 HTTPS REST API 访问后端；后端访问 MySQL 和私有对象存储；后台任务处理导出、扫描结果及通知。

浏览器 → HTTPS 网关 → Spring Boot 应用 → MySQL。

应用同时访问对象存储；数据库 outbox → 后台 worker → 通知与导出结果。通知失败不能撤销已提交的审批，但必须可重试、可追踪。

| 层次 | 建议技术 | 决策理由 |
|---|---|---|
| 后端运行时 | Java 21，Spring Boot 4 系列 | 独立新项目，采用受支持版本；实施时锁定依赖兼容矩阵 |
| 安全 | Spring Security，会话认证 | 面向同源企业网页，减少前端长期保存令牌 |
| 前端 | Vue 3、TypeScript、结构化富文本编辑器 | 章节和需求结构稳定，便于比对和权限处理 |
| 数据库 | MySQL，InnoDB，utf8mb4 | 利用事务、索引和并发控制维护审批一致性 |
| 数据访问 | Spring Data JPA，必要查询用显式 SQL | 通用聚合 CRUD 与复杂列表查询分开 |
| 迁移 | Flyway，发布流水线执行 | 数据库升级先验证，再发布应用 |
| 文件 | S3 兼容的私有对象存储 | 正文元数据与二进制内容分离 |
| 部署 | Linux、容器或 systemd、反向代理 | 第一阶段可单应用实例，后续再水平扩展 |

新项目不受现有 JDK 8 限制。Spring Boot 官方系统要求列出 4 系列最低 Java 17，本方案使用 Java 21；具体补丁、编辑器许可及对象存储产品在实施前确认。来源见第 13 节。

第一期不强制引入 Redis、Kafka、Elasticsearch 或独立流程引擎。会话采用 JDBC 持久化；outbox worker 与应用可同进程运行，但保持独立模块边界。搜索先实现标题、编号、标签和结构化条件过滤，不宣称具备高质量中文全文检索。

### 5.2 认证和授权

默认使用安全的 HttpOnly、Secure 会话 Cookie，SameSite=Lax；变更请求带 CSRF Token，登录后更新会话 ID，退出和停用账号时失效会话。密码使用 Spring Security 密码编码器单向散列，禁止明文存储和记录。会话空闲超时初始建议 30 分钟。

浏览器、导出、附件和列表查询共享同一授权服务，过滤项目范围后再分页与计数。伪造 project_id 不得读取其他项目；未知和无权访问的业务资源对外统一不可见响应，内部审计保留实际原因。

正文 HTML 服务端按白名单清洗；富文本 JSON 限制节点类型和大小。外部链接只允许 http 和 https；服务端不自动抓取链接或加载任意远程图片，避免 SSRF。

## 6 数据模型设计

### 6.1 关系约定

project 一对多 brd_document；brd_document 一对多 brd_version，且最多一个活动 brd_working_copy。brd_version 一对多 version_section、version_requirement 和 version_attachment。每次提交生成一份 review_instance 和一份待激活 approval_instance。approval_instance 一对多 approval_task。

工作稿使用受 schema_version 约束的 JSON 保存章节和需求结构；冻结时将正文和结构化条目复制为只读快照。评审和审批绑定 version_id，绝不绑定“最新工作稿”。

### 6.2 核心表

| 表 | 主要字段 | 约束和索引 |
|---|---|---|
| app_user | id、username、password_hash、status、auth_version | username 唯一；停用时增加 auth_version |
| project | id、code、name、owner_id、status | code 唯一 |
| project_member | project_id、user_id、roles_json、status | 唯一 project_id 与 user_id |
| brd_template | id、code、version_no、schema_json、status | code 与 version_no 唯一；发布后不可改 |
| brd_document | id、project_id、code、title、owner_id、status、current_published_version_id、active_working_copy_id、active_version_id、lock_version | project_id 与 code 唯一；项目状态时间复合索引 |
| brd_working_copy | id、document_id、base_version_id、content_json、schema_version、revision、status、change_request_id | 以 document 活动指针约束唯一活动工作稿 |
| brd_version | id、document_id、sequence_no、content_hash、template_snapshot、metadata_snapshot、status、submitted_by、submitted_at | document_id 与 sequence_no 唯一 |
| version_section | id、version_id、section_key、parent_key、sort_no、title、content_json | version_id 与 section_key 唯一 |
| version_requirement | id、version_id、requirement_key、section_key、title、description、priority、acceptance_json | version_id 与 requirement_key 唯一 |
| requirement_link | version_id、requirement_key、link_type、target_ref | 同版本同需求同链接唯一；不自动抓取 URL |
| attachment | id、project_id、object_key、sha256、size_bytes、mime、scan_status、created_by | object_key 唯一；按项目和扫描状态检索 |
| version_attachment | version_id、attachment_id、display_name、section_key | 引用组合唯一；历史对象不可覆盖 |
| review_instance | id、version_id、status、policy_snapshot | version_id 唯一 |
| review_task | id、instance_id、reviewer_id、status、completed_at | 同实例同评审人唯一 |
| comment_thread | id、version_id、section_key、requirement_key、anchor_json、severity、status、created_by | version_id 与 status 索引；锚点失效仍保留评论 |
| comment_message | id、thread_id、author_id、body、created_at | thread_id 与 created_at 索引 |
| approval_instance | id、version_id、policy_snapshot、current_step、status、lock_version | version_id 唯一 |
| approval_task | id、instance_id、step_no、assignee_id、status、decision、reason、acted_at | 实例节点人员唯一；assignee_id 与 status 索引 |
| change_request | id、document_id、base_version_id、reason、impact_json、status、result_version_id | document_id 与 status 索引 |
| audit_event | id、project_id、actor_id、action、object_type、object_id、request_id、detail_json、created_at | 项目时间索引；应用只追加，不更新删除 |
| outbox_event | id、event_type、aggregate_id、payload_json、status、attempts、next_retry_at、lease_until | status 与 next_retry_at 索引 |
| notification | id、recipient_id、event_id、content、read_at | recipient_id 与 event_id 唯一 |
| export_job | id、version_id、requester_id、status、object_key、error_code、expires_at | 请求者与时间索引；下载重新鉴权 |
| idempotency_record | actor_id、operation、idempotency_key、request_hash、result_ref、expires_at | actor_id、operation、idempotency_key 唯一 |

所有业务表用 BIGINT 主键，时间存 UTC 的 DATETIME(3)，前端按用户时区显示。可变聚合包含 created_at、updated_at 和乐观锁；审批决策和审计记录只追加。正文全文及密码、Cookie、令牌不得进入审计 detail_json。

外键与服务层联合验证 version_id、document_id、project_id 的归属。数据层约束不得允许同项目以外的附件被引用；基于父对象计算项目归属，忽略前端声称的归属。

### 6.3 不可变性和并发

冻结内容使用确定性的 JSON 序列化：固定字段顺序、按稳定 ID 排序的引用、UTF-8 编码，再计算 SHA-256。哈希只覆盖业务内容、元数据、需求和附件摘要，不覆盖会变化的审批状态。导出任务读取冻结内容，不读取工作稿。

提交事务先锁 brd_document，再校验活动工作稿、revision 和状态，分配 sequence_no，插入完整版本快照与任务，更新活动指针并写入审计和 outbox。同一文档所有提交、撤回、审批终结和发布操作遵循一致锁顺序，减少死锁；发生死锁时有限重试并使用幂等键。

审批在同一事务中校验操作者、任务当前状态、版本状态并更新任务；最后一位会签人触发下一节点。幂等键复用但请求体不同返回 409。幂等记录建议保留 7 天，过期后仍由任务终态保证不能重复审批。

## 7 接口设计

接口前缀为 /api/v1，JSON 编码为 UTF-8。列表默认每页 20 条，上限 100 条，排序字段白名单；请求正文大小初始限制 2 MB。id 为字符串化数值，避免 JavaScript 数值精度损失。

| 方法及路径 | 用途 | 主要约束 |
|---|---|---|
| POST /auth/login | 登录并建立会话 | 登录限流、统一失败提示 |
| POST /auth/logout | 退出 | 服务端失效会话 |
| GET /projects | 可访问项目列表 | 按成员过滤 |
| POST /projects | 创建项目 | 明确授权的创建者 |
| PUT /projects/{id}/members/{userId} | 配置成员角色 | 项目负责人；记录审计 |
| POST /projects/{id}/brds | 创建 BRD 和工作稿 | 作者角色；模板已发布 |
| GET /brds/{id} | 查询文档及版本概览 | 按角色过滤可见内容 |
| PUT /brds/{id}/working-copy | 保存工作稿 | If-Match 必填；仅 EDITING |
| POST /brds/{id}/submissions | 冻结并提交 | revision、评审人、审批快照、幂等键 |
| GET /versions/{id} | 读取冻结版本 | 未发布版本按任务和角色授权 |
| GET /brds/{id}/diff?from=1&to=2 | 版本差异 | 两版本均归属同 BRD 且可访问 |
| POST /review-tasks/{id}/decisions | 完成或退回评审 | 本人有效任务；退回给理由 |
| POST /approval-tasks/{id}/decisions | 批准或退回 | 当前节点本人任务、禁止自批 |
| POST /versions/{id}/withdrawals | 撤回 | 仅在途版本、带原因 |
| POST /versions/{id}/publications | 发布 | 已批准、负责人、基线一致 |
| POST /brds/{id}/changes | 创建变更和工作稿 | 基于当前已发布版本 |
| POST /versions/{id}/comments | 创建评论线程 | 锚点和版本绑定 |
| POST /projects/{id}/attachments | 上传附件 | 隔离存储和扫描 |
| POST /versions/{id}/exports | 创建 PDF 导出任务 | 返回 202 和 jobId |
| GET /exports/{id} | 获取任务和下载入口 | 重新检查用户访问权限 |
| GET /projects/{id}/audit-events | 查询审计 | 审计权限、分页 |

保存工作稿示例：请求头 If-Match: "12"，正文为 {"sections":[...],"requirements":[...]}；成功返回新 revision 13 和 ETag "13"。缺少 If-Match 返回 428，版本不匹配返回 409，不覆盖服务器内容。

审批示例：POST /approval-tasks/9001/decisions，Idempotency-Key 为客户端生成的 UUID，正文 {"decision":"APPROVE","expectedStatus":"PENDING","comment":"同意"}。服务端不接受请求体中的操作者身份，以认证会话为准。

统一错误体示例：{"code":"VERSION_CONFLICT","message":"文档已更新，请比较后重试","requestId":"...","details":{"currentRevision":13}}。使用 400 参数错误、401 未登录、403 操作权限不足、404 资源不可见、409 状态冲突、413 过大、422 提交规则不满足、429 限流。日志包含 requestId，不回显数据库凭据或堆栈。

## 8 通知与审计

站内通知覆盖评审分配、审批分配、退回、发布、评论提及和超时提醒。事务中只写 outbox；worker 带租约领取事件，失败指数退避，建议最多 10 次后进入人工处理队列。消费者按 event_id 去重，不能承诺网络层恰好一次投递。

审计至少记录创建、编辑、提交、评审结果、审批结果、发布、变更、权限变更、附件下载、导出、归档和登录异常。记录用户、项目、时间、对象版本、操作结果和 requestId，正文修改保存结构化差异摘要而非完整敏感正文。

第一期审计为应用权限控制下的追加日志，不宣称能够防止数据库管理员篡改。高合规场景应增加外部只写日志存储或不可变归档，作为后续独立范围。

## 9 部署与数据安全

新项目使用独立数据库 brd_management；不得连接现有 pet_db 执行迁移。应用账号只具有本库必要 DML 权限，迁移账号持有本库 DDL 权限并只在部署阶段使用；生产应用启动时关闭自动迁移和 Hibernate 自动建表。

开发、测试、生产三套配置分离。密码和对象存储密钥由运行环境或密钥服务提供，不写入仓库、设计说明书、镜像或共享 IDEA 配置。网络只向需要的网段开放数据库端口，应用与数据库跨主机通信使用证书验证的 TLS。

部署步骤为：备份及恢复验证 → 对副本执行迁移 → 执行向后兼容的生产迁移 → 发布应用 → 健康和业务冒烟 → 切流。数据库变更采用 expand-contract；应用回退前确认兼容旧版本，不能把“执行反向 SQL”当作无损回滚保证。

存活探针仅判断进程工作状态；就绪探针检查数据库及必要依赖。监控 HTTP 错误率、P95 延迟、连接池、慢 SQL、待办积压、扫描失败、outbox 重试和备份结果。日志统一 requestId，并清除凭据和正文。

建议每天全量备份、开启 binlog，并对附件启用版本保留。RPO 15 分钟、RTO 4 小时作为初始目标，需以恢复演练验证；每季度进行一次独立环境恢复，校验版本内容哈希和附件是否齐全。

## 10 非功能要求与验收

### 10.1 容量与性能目标

建议基准为 200 名注册用户、50 个并发活跃用户、10 万份 BRD、100 万份版本元数据。单 BRD 冻结内容不超过 2 MB，附件不计入正文限制。目标必须在声明的测试硬件和固定数据集上测量，不视为当前系统已达到的结果。

50 并发条件下，列表和详情接口 P95 小于 1 秒；草稿保存和审批接口 P95 小于 1.5 秒；20 页无大型图片的 PDF 导出 P95 小于 30 秒。统计至少覆盖 15 分钟稳定负载，记录错误率、峰值内存和数据库资源。

### 10.2 验收矩阵

| 编号 | 场景 | 通过条件 |
|---|---|---|
| AT01 | 完整审批发布 | 冻结内容、审批记录和发布指针一致 |
| AT02 | 作者自批 | 前后端均拒绝；审计记录失败结果 |
| AT03 | 跨项目访问 | 正文、附件、导出、计数均不泄露 |
| AT04 | 两人并发保存 | 一次成功，一次 409；本地修改可恢复 |
| AT05 | 审批重复点击 | 只产生一次有效决策及一次后续事件 |
| AT06 | 同时会签 | 只激活一次下一节点，不丢失任何批准 |
| AT07 | 退回后重交 | 旧版本和意见保留，新版本使用新序号 |
| AT08 | 发布后修改 | 原发布版本不可改，新工作稿走变更流程 |
| AT09 | 越权或伪造任务 | 服务端拒绝，不改变实例状态 |
| AT10 | 附件带恶意内容 | 扫描未通过时无法提交或下载 |
| AT11 | 模板升级 | 历史 BRD 模板和内容不变 |
| AT12 | 通知发送失败 | 审批正常提交，事件可重试且通知去重 |
| AT13 | 用户停用 | 旧会话失效，在途任务不能继续执行 |
| AT14 | 恢复演练 | 数据和附件一致，达到确认后的 RPO 与 RTO |
| AT15 | PDF 历史导出 | 与指定冻结版本一致，含编号、版本和状态 |
| AT16 | 旧基线发布 | 基线不一致返回冲突，不覆盖新发布指针 |

单元测试覆盖状态转换、权限规则和内容摘要；集成测试使用真实 MySQL 兼容环境，验证事务、唯一约束和锁；端到端测试覆盖创建至二次发布。上线前完成依赖漏洞扫描、附件安全测试和备份恢复测试。

## 11 开发计划

建议按两个后端或全栈工程师、一个前端工程师估算，产品和测试提供持续支持。以下为约 6 至 8 周的初步排期，不是人员已到位或交付日期承诺。

| 阶段 | 时间估算 | 可验收成果 |
|---|---|---|
| 设计确认 | 约 1 周 | 页面原型、状态机、ER 模型、接口契约、范围签字 |
| 基础与编辑 | 约 2 周 | 登录、项目权限、模板、工作稿、附件隔离 |
| 版本与流程 | 约 2 周 | 冻结、对比、评审、审批、幂等、审计 |
| 发布与交付 | 约 1 周 | 发布变更、通知、PDF 导出、管理页面 |
| 联调与上线 | 约 1 至 2 周 | 集成及端到端测试、压测、恢复演练、试运行 |

优先交付一个无分支的完整流程，再增加会签和异常恢复。避免先完成全部页面后才验证审批事务和版本模型。

## 12 待确认决策与风险

| 决策 | 当前设计默认值 | 改变时的影响 |
|---|---|---|
| 服务对象 | 企业内部单组织 | 多租户需要重做数据、文件及运维隔离 |
| 身份来源 | 本地账号 | SSO 需要身份映射、停用和会话同步 |
| 审批复杂度 | 串行节点内全员会签 | 条件分支和委托增加流程状态与审计复杂度 |
| 文档格式 | 结构化富文本与 PDF 导出 | 保真 Word 导入导出需独立评估和测试 |
| 历史数据 | 新项目从空库开始 | 导入旧 BRD 需提供来源、映射和归档规则 |
| 保留期限 | 初拟审计与发布版本至少 3 年 | 由业务和合规确认，不能自动清理待诉讼或保留中的材料 |
| 交付资源 | 3 名开发，产品测试协作 | 实际人数与经验影响排期 |

最高风险是审批与正文版本不一致，通过不可变版本和哈希约束解决；其次是跨项目数据泄露，通过统一授权与负向测试控制；最后是流程范围膨胀，通过第一期固定流程与后续扩展边界管理。

## 13 技术参考

以下为技术兼容性和安全机制参考，不代表产品需求来自这些文档。

- Spring Boot 官方系统要求：https://docs.spring.io/spring-boot/system-requirements.html
- Spring Security 会话管理：https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html
- Spring Security CSRF 防护：https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html

框架补丁版本、数据库发行版支持周期和依赖许可证应在正式创建可运行代码时再次核对并锁定。
