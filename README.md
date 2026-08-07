# SwizuNotes

SwizuNotes 是一个从零开始构建的个人博客系统，目前正处于开发阶段。项目的核心目标是打造一个轻量、安全、自包含的写作与发布平台，同时作为我个人技术实践的主要载体。

---

### 技术栈

- 后端框架：Spring Boot 4.1.0
- 编程语言：Java 25
- 构建工具：Gradle
- 数据库：PostgreSQL
- ORM：Spring Data JPA
- 安全框架：Spring Security + JWT（jjwt 0.12.6）
- 参数校验：Jakarta Validation（Bean Validation 3.0）
- 前端框架：Vue 3 + Vite

---

### 环境要求

- JDK 25
- PostgreSQL（本地库名：`swizunotes`）
- Node.js 22.18+（前端，可选）

---

### 快速开始

#### 后端

1. 创建数据库：`CREATE DATABASE swizunotes;`
2. 在 `server/src/main/resources/application.yml` 中配置数据库连接
3. 启动：

```sh
cd server
./gradlew bootRun
```

#### 前端

```sh
cd client
npm install
npm run dev
```

---

### 项目结构

#### 后端结构

server/src/main/java/com/swizu/swizunotes/

- `ServerApplication.java` ：启动类
- `controller/` ：控制器（只做参数绑定与响应封装）
- `services/` ：业务层（业务规则、事务边界）
- `repository/` ：数据访问层（Spring Data JPA）
- `entity/` ：实体类
- `dto/request/` ：请求 DTO
- `dto/response/` ：响应 DTO
- `dto/MediaMetadata/` ：Media 实体 `metadata`（jsonb）字段的值对象
- `config/` ：配置类（SecurityConfig 等）
- `filter/` ：JWT 认证过滤器
- `common/` ：通用类（Result 响应包装、全局异常处理器、自定义异常）
- `util/` ：工具类（JwtUtils 等）

server/src/main/resources/

- `application.yml` ：主配置文件

#### 前端结构

client/

- Vue 3 + Vite 工程，开发中

---

### API 约定

所有接口统一使用 `/api/v1` 前缀。

#### 响应格式

成功响应统一为：

```json
{
  "message": "操作提示信息",
  "data": {}
}
```

#### HTTP 状态码

- `200` ：成功
- `400` ：请求参数错误
- `401` ：未认证
- `403` ：无权限
- `404` ：资源不存在
- `500` ：服务器内部错误

业务层抛出 `ResourceNotFoundException` / `ForbiddenException` / `UnauthorizedException`，由 `GlobalExceptionHandler` 统一转换为对应的状态码和 `Result` 响应体。

#### 认证

除登录接口（`POST /api/v1/session/`）与部分公开 GET 接口外，其余接口需在请求头携带：

```
Authorization: Bearer <JWT>
```

---

### 开发路线图

- [x] 项目初始化
- [x] 数据库表结构设计
- [x] JWT 认证实现
- [x] 文章 CRUD 接口（基础版）
- [ ] 媒体上传与管理
- [ ] 注册接口
- [ ] 前端页面开发
- [ ] 部署上线

详细任务见 `server/TODO.md`。

---

### 许可证

GPLv3
