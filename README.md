# SwizuNotes

SwizuNotes 是一个从零构建的个人博客系统：**轻量、安全、自包含的写作与发布平台**。项目功能已基本完成，处于上线准备阶段。

---

## 功能特性

- **写作与发布**：Markdown 编辑器（md-editor-v3，所见即所得预览）、草稿/发布双状态、自动保存、空白草稿预留 id 复用、文章目录（与渲染计数同源，代码块不误入目录）、全文搜索
- **媒体系统**：图片/音频/视频/文件上传（魔数核查 + SVG 拒绝）、HTTP Range 分段播放（视频 Seek）、音频 ID3 元数据提取、音频封面、媒体库管理、级联删除（删音频删封面 / 删文章删全部媒体 / 换封面删旧封面）
- **主题系统**：主题由后端管理（配置 + CSS + 亮暗 banner），发布三态（未发布/预发布/已发布）、公历/农历日期自动切换、深浅模式与专属深色变体、未发布主题预览（令牌 + 持久预览浮条）、View Transitions 切换动画、CSS 外域引用净化、默认主题 default 保护
- **用户体系**：登录（Access+Refresh+Media 三令牌）、退出即时失效（jti 黑名单）、登录限流、头像上传（裁剪 + 魔数核查 + 默认半身像占位图）、昵称、用户主页（本人含草稿）
- **安全加固**：正文/预览 DOMPurify 消毒、CSP、媒体令牌不进 URL、统一异常处理（不泄露内部细节）、管理员权限隔离

---

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 4.1.0 / Java 25 / Gradle |
| 数据库 | PostgreSQL（库名 `swizunotes`，DDL 手动维护） |
| ORM / 安全 | Spring Data JPA / Spring Security + JWT（jjwt 0.12.6） |
| JSON / 校验 | Jackson 3（`tools.jackson.*`）/ Jakarta Validation |
| 前端 | Vue 3 + Vite + TypeScript（`client-ts/`） |
| 前端依赖 | vue-router、pinia、axios、marked、md-editor-v3、artplayer、music-metadata、viewerjs、cropperjs |
| 其他 | cn.6tail:lunar（农历日期）、DOMPurify |

---

## 环境要求

- JDK 25
- PostgreSQL
- Node.js 22+

---

## 快速开始

### 1. 初始化数据库

```sql
CREATE DATABASE swizunotes;
-- 执行 server/ddl/init.sql（建表 + 初始账号 admin/123456）
```

### 2. 配置后端

- 数据库密码通过环境变量 `DB_PASSWORD` 提供
- JWT 密钥通过本地配置 `server/src/main/resources/application-local.yml`（git 已忽略）或生产环境变量 `JWT_SECRET` 提供

```sh
cd server
$env:DB_PASSWORD="你的密码"; .\gradlew.bat bootRun
```

### 3. 启动前端

```sh
cd client-ts
npm install
npm run dev   # http://localhost:5173（/api 代理到 http://localhost:8080）
```

### 默认账号

| 账号 | 密码 | 角色 |
|---|---|---|
| admin | 123456 | 管理员（主题管理） |

---

## 项目结构

```
├── server/                          # 后端（Spring Boot）
│   ├── ddl/init.sql                 # 数据库初始化脚本
│   └── src/main/java/com/swizu/swizunotes/
│       ├── controller/              # 控制器（参数绑定与响应封装）
│       ├── services/                # 业务层（文章/媒体/主题/头像/存储等）
│       ├── repository/              # 数据访问层
│       ├── entity/                  # 实体 + MediaMetadata 值对象
│       ├── dto/request|response/    # 请求/响应 DTO
│       ├── config/                  # SecurityConfig
│       ├── filter/                  # JWT 认证过滤器
│       ├── common/                  # Result 包装、全局异常、自定义异常
│       └── util/                    # JwtUtils（access/refresh/media 令牌）
└── client-ts/                       # 前端（Vue 3 + Vite + TS）
    ├── public/                      # 静态资源（横幅图、主题兜底 CSS、防闪烁脚本）
    └── src/
        ├── api/                     # HTTP 层（401 自动刷新重试）
        ├── stores/                  # pinia（用户/主题）
        ├── theme/                   # 主题切换（后端 CSS 来源 + 本地兜底）
        ├── utils/                   # 文章渲染（marked + DOMPurify）、toast
        ├── composables/             # 媒体卡片播放器
        ├── views/                   # 首页/文章/编辑器/登录/管理/用户/搜索
        └── components/              # 导航栏/主题选择器/媒体库/头像裁剪等
```

---

## API 约定

统一前缀 `/api/v1`；响应统一为 `Result { message, data }`。

| 状态码 | 含义 |
|---|---|
| 200 / 204 | 成功 / 删除成功 |
| 400 / 401 / 403 / 404 | 参数错误 / 未认证 / 无权限 / 资源不存在 |
| 500 | 服务器内部错误（不泄露内部细节） |

认证：请求头 `Authorization: Bearer <accessToken>`；媒体读取 query 携带媒体专用令牌 `?token=`（12h）；退出登录后三类令牌立即失效。

---

## 上线注意事项

- **CORS**：开发期全开（无凭证）；上线前在 `SecurityConfig` 收紧为站点自身域名白名单
- **密钥**：生产使用环境变量 `JWT_SECRET`；不要提交任何真实密码
- **令牌黑名单/预览令牌为内存态**：多实例部署需替换为 Redis 等共享存储
- **上传目录**：`server/uploads/`（媒体/头像/主题文件）已 gitignore，需纳入备份
- **前端生产构建**：`npm run build`，主题 CSS 全部由后端提供（本地仅 default.css 兜底）

---

## 图片许可

项目中出现的全部主题 banner 图片以
[知识共享 署名—非商业性使用—相同方式共享 4.0 国际（CC BY-NC-SA 4.0）](https://creativecommons.org/licenses/by-nc-sa/4.0/) 协议共享：
协议全文见 [LICENSE-IMAGES.md](./LICENSE-IMAGES.md)。

---

## 开发路线图

- [x] 项目初始化与数据库设计
- [x] JWT 认证（三令牌 + 黑名单 + 限流）
- [x] 文章 / 媒体 / 主题 / 用户 / 搜索 全部功能
- [x] 安全加固与全项目复审（全功能测试 78/78 通过）
- [x] 前端 UI 体系（桌面 + 移动端）
- [ ] 部署上线（CORS 收紧、生产配置、备份策略）

---

## 许可证

GPLv3
