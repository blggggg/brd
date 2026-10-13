# BRD 管理系统

已实现本地可运行的文件版本管理和账号管理，不含审批。

## 技术

- 后端：Java 17、Spring Boot 4、Spring Security、JDBC、Flyway。
- 前端：原生 HTML + CSS + JavaScript；Vite 只用于开发和打包，不使用 Vue/TypeScript。
- 当前本地开发数据库：H2 文件库。不要复用 pet 项目的数据库或密码。

## 使用

前后端源码都在 `backend` Spring Boot 项目内：

```text
backend/
├─ pom.xml
├─ src/main/java/           Java 后端源码
├─ src/main/resources/      后端配置及数据库迁移
├─ src/main/frontend/       HTML、CSS、JavaScript、前端依赖及测试
│  ├─ index.html
│  ├─ src/
│  └─ package.json
└─ target/brd-management-0.1.0.jar
```

原根目录 `frontend` 已移入 `backend/src/main/frontend`。构建产物会复制到 `target/classes/static` 并打入 JAR，不把生成文件写入源码资源目录。

页面和接口统一地址 `http://127.0.0.1:8091/`。前端已打入 Spring Boot JAR，运行时只需要 Java 17，不需要 Node.js 或前端开发服务器。

从源码构建需要 Java 17、Maven 3.9、Node.js 20 和 npm。在 `backend` 目录运行 `mvn clean verify`，会自动安装前端依赖、运行前端测试、构建前端、复制资源并打包。生成文件为 `backend/target/brd-management-0.1.0.jar`。

在 `backend` 目录运行（Windows 也可在项目根目录执行 `powershell -ExecutionPolicy Bypass -File .\start.ps1`）：

```powershell
java -jar target/brd-management-0.1.0.jar
```

必须从同一个 backend 工作目录启动，才能使用同一份 `data` 文件数据库。首次创建空库前请设置至少 12 位的 `BRD_ADMIN_PASSWORD` 环境变量；已有账号不受该变量影响。首次登录需更改临时密码。默认配置里的旧示例密码不应作为部署密码。

## 已实现

文档列表、标题/项目/批次搜索、分页、新建文档、PDF/DOCX 上传、变更说明、版本历史、内容去重、下载、手动切换当前使用版（可回退）、操作记录、元数据编辑和管理员转交负责人。

管理员可创建账号、修改显示名称与角色、启停账号、重置密码。上传者只能修改自己负责的文档，阅读者只能查看和下载。首次修改密码和会话失效均由后端校验。

上传新版本不会自动替代当前使用版，第一版除外。历史版本不可覆盖。不包含审批、在线编辑、文件语义比较或分支合并。

## 测试

`backend/src/main/frontend`: `npm test`、`npm run build`。单 JAR 浏览器验收：设置 `JAVA_HOME` 为 Java 17，在该目录运行 `npx playwright test --config=playwright.jar.config.js`。HTTP 兼容性验收使用 `--config=playwright.http.config.js`。测试自动在 8092 启动同一个 JAR，使用隔离的内存数据库，不启动 Vite。开发模式仍可在该目录运行 `npm run dev` 使用 5173 热更新。

`backend`: `mvn test` 使用内存数据库验证登录/改密、CSRF、上传去重、当前版切换/回退、下载、账号权限、停用和密码重置，不改动实际业务库。

## 限制

当前为本地开发版。结构校验不是完整杀毒，生产上线前需要配置 ClamAV、HTTPS、数据库账号、强密码、备份和监控。数据库和上传文件保存在 backend/data 中，独立于 JAR；移动程序时要保留此目录，不能只搬走 JAR 后丢弃业务数据。

原设计文档在 `docs` 中，技术选型以本说明为准。
