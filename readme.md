# 🛒 jooShop / shop-flashsale

基于 Spring Cloud Alibaba 的秒杀电商平台（多模块微服务架构），包含前后端、网关、认证、秒杀服务、积分、支付，以及一个独立的 AI 服务模块。

> 根目录结构：
>
> - `ai-service`：AI 能力相关微服务  
> - `frontend-server`：前端页面服务（静态资源）  
> - `shop-parent`：后端核心工程（多模块微服务）

---

## 🧩 技术栈简介

- **后端框架**：Spring Boot, Spring Cloud Alibaba
- **服务治理**：Nacos（配置中心 & 注册中心）
- **持久化 & 缓存**：MySQL, Redis
- **消息队列**：RocketMQ（用于削峰、异步下单等）
- **网关**：Spring Cloud Gateway（`api-gateway`）
- **认证与权限**：自定义 UAA 服务（`shop-uaa`）
- **前端**：静态页面 + LayUI（`frontend-server`）
- **其他**：WebSocket 实时推送、定时任务、Canal 数据同步（待补充）

> 具体依赖可参考各模块的 `pom.xml`。

---

## 🧩 模块说明（Module Overview）

### 🌳 ai-service

路径：`ai-service/`

- 独立的 Spring Boot 微服务
- 包结构：
  - `cn.wolfcode.ai.domain`：AI 相关领域模型
  - `cn.wolfcode.ai.web`：Controller / 接口层
- 负责对接大模型 / 提供智能问答、推荐等 AI 能力---（目前只有页面显示，待完善）

---

### 🌳 frontend-server

路径：`frontend-server/`

- 提供静态页面资源
- 目录结构：
  - `src/main/resources/static/assets`：CSS / JS / 图片 / LayUI 等前端资源
  - `src/main/resources/static/img`：页面图片
- 典型场景：商品列表、秒杀页面、下单页面等 界面，由后端接口提供数据。

---

### 🌳 shop-parent（后端核心工程）

路径：`shop-parent/`

这是整个项目的 **后端主工程**，包含所有微服务模块。

#### ⚙️api-gateway

- 路径：`shop-parent/api-gateway`
- 基于 Spring Cloud Gateway
- 功能：
  - 统一入口
  - 路由转发到各个微服务
  - 后续可扩展：登录校验、限流、灰度发布等

#### ⚙️ canal-client

- 路径：`shop-parent/canal-client`
- 待完善

#### ⚙️ shop-common

- 路径：`shop-parent/shop-common`
- 公共模块，被其他服务依赖

#### ⚙️ shop-provider（业务服务）

路径：`shop-parent/shop-provider/`

包含多个核心业务微服务：

- **intergral-server**
  - 用户积分服务
  - 负责积分累积、积分记录等

- **job-server**
  - 定时任务服务
  - 包含：定时任务、远程调用（feign）、Redis 工具等

- **pay-server**
  - 支付服务
  - 封装与支付相关的接口（如下单支付、回调处理等）

- **product-server**
  - 商品服务
  - 包含商品 Mapper、Service、Controller 等

- **seckill-server**
  - 核心秒杀服务
  - 包含：
    - 缓存层（`cache`）
    - 远程调用（`feign`）
    - MQ 消费（`mq.listener`）
    - 业务逻辑（`service.impl`）
    - Web 接口 & 统一响应（`web.controller` / `web.advice`）
    - Lua 脚本等（`resources/META-INF/scripts`）

#### ⚙️ shop-provider-api（服务 API 定义）

路径：`shop-parent/shop-provider-api/`

- 对应各业务服务的 API & DTO 模块：
  - `intergral-api`
  - `pay-api`
  - `product-api`
  - `seckill-api`
- 用于服务间调用时复用领域模型和接口定义。

#### ⚙️ shop-uaa（认证中心）

- 路径：`shop-parent/shop-uaa`
- 用户认证与授权服务
- 包含：
  - 用户实体 / Mapper / Service
  - 登录 / 注册 / Token 等接口
  - Redis 缓存 & MQ 等

#### ⚙️ websocket-server

- 路径：`shop-parent/websocket-server`
- WebSocket 长连接服务
- 用于秒杀结果推送、订单状态推送等实时消息通知

---

## 🌳 基本运行说明

1. 准备基础环境：
   - JDK 1.8+ / （ai-service）17+
   - Maven
   - MySQL
   - Redis
   - RocketMQ
   - Nacos

2. 导入项目：
   - 使用 IntelliJ IDEA 以 Maven 多模块工程方式导入 `shop-parent`、`frontend-server`、`ai-service`。

3. 启动顺序建议：
   1. 基础设施：Nacos、MySQL、Redis、RocketMQ
   2. 公共 & 核心服务：`shop-common`、`shop-uaa`、`product-server`、`seckill-server`、`pay-server`、`intergral-server`、`job-server`、`websocket-server`
   3. 网关：`api-gateway`
   4. 前端静态服务：`frontend-server`
   5. AI 服务：`ai-service`（用于扩展AI）

---

## 🌳 TODO / 后续计划

- [ ] 完善 AI 服务功能说明
- [ ] 添加压测结果与架构设计说明

