# 天机学堂 (Tianji Learning Platform)

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Java](https://img.shields.io/badge/Java-11-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.2-brightgreen.svg)
![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2021.0.3-brightgreen.svg)

> 一个基于微服务架构的在线教育学习平台

## 项目简介

天机学堂是一个采用现代微服务架构设计的全功能在线教育平台。该项目集成了课程管理、在线学习、考试评估、订单支付、用户社交等完整的教育平台功能，是学习和实践企业级Java应用开发的优秀案例。

## 主要特性

- 微服务架构设计，14个独立服务模块
- 完整的用户、课程、学习、交易、支付流程
- 分布式服务治理（Nacos + Sentinel + Seata）
- 多级缓存架构（Redis + Caffeine）
- 全文搜索（Elasticsearch）
- 分布式任务调度（XXL-Job）
- 多云存储支持（阿里云 + 腾讯云）
- 完善的权限认证体系
- API网关统一入口

## 技术栈

### 核心框架

- **Spring Boot**: 2.7.2
- **Spring Cloud**: 2021.0.3
- **Spring Cloud Alibaba**: 2021.0.1.0
- **JDK**: 11

### 微服务组件

| 组件 | 版本 | 说明 |
|-----|------|-----|
| Nacos | 2021.0.1.0 | 服务注册发现、配置中心 |
| Sentinel | 2021.0.1.0 | 流量控制、熔断降级 |
| Seata | 1.5.1 | 分布式事务 |
| Gateway | 2021.0.3 | API网关 |
| OpenFeign | 2021.0.3 | 服务间调用 |
| LoadBalancer | 2021.0.3 | 负载均衡 |

### 数据存储

- **MySQL**: 8.0.23
- **Redis**: Spring Boot Data Redis
- **Elasticsearch**: 7.12.1
- **MyBatis Plus**: 3.4.3

### 中间件

- **RabbitMQ**: Spring Boot AMQP
- **XXL-Job**: 2.3.1
- **Redisson**: 3.13.6

### 云服务集成

- **阿里云**: OSS、KMS、支付宝支付
- **腾讯云**: COS、VOD（视频点播）

### 工具库

- **Hutool**: 5.7.17
- **Lombok**: 1.18.20
- **Knife4j (Swagger)**: 3.0.3

## 系统架构

### 服务模块

```
天机学堂微服务架构
│
├── tj-gateway              # API网关 (端口动态)
├── tj-auth-service         # 权限认证服务 (8081)
├── tj-user                 # 用户服务 (8082)
├── tj-course               # 课程服务
├── tj-learning             # 学习服务
├── tj-exam                 # 考试服务
├── tj-trade                # 交易服务
├── tj-pay-service          # 支付服务
├── tj-promotion            # 促销服务
├── tj-search               # 搜索服务
├── tj-media                # 媒体服务
├── tj-message-service      # 消息服务
├── tj-data                 # 数据分析服务
└── tj-remark               # 评论服务
```

### 模块说明

| 模块 | 功能描述 |
|-----|---------|
| **tj-common** | 公共模块，提供通用工具类和基础组件 |
| **tj-api** | API SDK模块，提供OpenFeign接口定义 |
| **tj-auth** | 权限管理，包含认证、授权、菜单、角色管理 |
| **tj-user** | 用户管理，用户注册、登录、信息维护 |
| **tj-course** | 课程管理，课程发布、分类、教师管理 |
| **tj-learning** | 学习服务，学习进度、积分、优惠券、学习记录 |
| **tj-exam** | 考试管理，试题、试卷、考试记录 |
| **tj-trade** | 交易服务，订单、购物车、退款处理 |
| **tj-pay** | 支付服务，支付渠道、支付订单管理 |
| **tj-promotion** | 促销活动，优惠券、秒杀活动 |
| **tj-search** | 搜索服务，基于Elasticsearch的全文搜索 |
| **tj-media** | 媒体管理，文件存储、视频处理 |
| **tj-message** | 消息服务，消息推送、短信、邮件通知 |
| **tj-data** | 数据分析，学习数据统计和分析 |
| **tj-remark** | 评论评价，用户评价、点赞、评论 |

## 快速开始

### 环境要求

- JDK 11+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+
- RabbitMQ 3.8+
- Elasticsearch 7.12.1
- Nacos 2.0+
- XXL-Job 2.3.1

### 本地开发

1. **克隆项目**

```bash
git clone <your-repository-url>
cd tianji
```

2. **配置Nacos**

启动Nacos服务器，并在Nacos配置中心创建以下共享配置：

- `shared-spring.yaml` - Spring通用配置
- `shared-redis.yaml` - Redis配置
- `shared-mybatis.yaml` - MyBatis配置
- `shared-log.yaml` - 日志配置
- `shared-feign.yaml` - Feign配置

3. **配置数据库**

创建所需数据库并执行初始化脚本（如果提供）。

4. **修改配置**

修改各服务的 `bootstrap.yml` 或 `bootstrap-local.yml`，配置Nacos连接信息。

5. **启动服务**

建议按以下顺序启动服务：

```bash
# 1. 启动基础服务（Nacos、Redis、MySQL、RabbitMQ、Elasticsearch）

# 2. 启动权限服务
cd tj-auth/tj-auth-service
mvn spring-boot:run

# 3. 启动API网关
cd tj-gateway
mvn spring-boot:run

# 4. 启动其他业务服务
# tj-user, tj-course, tj-learning, etc.
```

6. **访问服务**

- API网关: `http://localhost:{gateway-port}`
- Swagger文档: `http://localhost:{service-port}/doc.html`

## 配置说明

### 配置文件层级

- **bootstrap.yml**: 基础配置，Nacos连接配置
- **bootstrap-dev.yml**: 开发环境配置
- **bootstrap-local.yml**: 本地环境配置

### Nacos配置中心

所有服务通过Nacos统一管理配置，共享配置包括：

- Spring基础配置
- 数据源配置
- Redis配置
- MyBatis配置
- Feign配置
- 日志配置
- Swagger配置

### 环境变量

可通过环境变量配置：

- `JAVA_OPTS`: JVM参数配置
- `NACOS_ADDR`: Nacos服务地址
- `NACOS_NAMESPACE`: Nacos命名空间

## 部署

### Docker部署

项目包含Dockerfile，支持Docker容器化部署：

```bash
# 构建镜像
docker build -t tianji-service:latest .

# 运行容器
docker run -d \
  -p 8080:8080 \
  -e JAVA_OPTS="-Xms512m -Xmx1024m" \
  tianji-service:latest
```

### 容器环境

- 基础镜像: OpenJDK 11
- 时区: Asia/Shanghai
- 工作目录: /app

## API文档

每个服务集成了Knife4j（Swagger增强版），提供交互式API文档。

访问方式：`http://{service-host}:{service-port}/doc.html`

## 数据库设计

主要数据表：

### 权限模块
- user - 用户表
- role - 角色表
- privilege - 权限表
- menu - 菜单表
- account_role - 账户角色关联表

### 课程模块
- course - 课程表
- course_content - 课程内容表
- course_teacher - 课程教师表
- course_catalogue - 课程目录表

### 学习模块
- learning_lesson - 学习课程表
- learning_record - 学习记录表
- points_record - 积分记录表
- interaction_question - 互动问题表

### 交易支付
- order - 订单表
- order_detail - 订单详情表
- pay_order - 支付订单表
- cart - 购物车表

（更多详情请参考数据库文档）

## 项目结构

```
tianji/
├── tj-common/                  # 公共模块
├── tj-auth/                    # 权限服务
│   ├── tj-auth-common/
│   ├── tj-auth-service/
│   ├── tj-auth-resource-sdk/
│   └── tj-auth-gateway-sdk/
├── tj-api/                     # API SDK
├── tj-gateway/                 # API网关
├── tj-user/                    # 用户服务
├── tj-course/                  # 课程服务
├── tj-learning/                # 学习服务
├── tj-exam/                    # 考试服务
├── tj-trade/                   # 交易服务
├── tj-pay/                     # 支付服务
│   ├── tj-pay-service/
│   ├── tj-pay-domain/
│   └── tj-pay-api/
├── tj-promotion/               # 促销服务
├── tj-search/                  # 搜索服务
├── tj-media/                   # 媒体服务
├── tj-message/                 # 消息服务
│   ├── tj-message-service/
│   ├── tj-message-domain/
│   └── tj-message-api/
├── tj-data/                    # 数据分析服务
├── tj-remark/                  # 评论服务
├── job/                        # 定时任务配置
├── pom.xml                     # 父POM
└── README.md                   # 项目文档
```

## 开发规范

### 代码规范

- 遵循阿里巴巴Java开发规范
- 使用Lombok简化代码
- 统一异常处理
- RESTful API设计

### Git提交规范

- feat: 新功能
- fix: 修复bug
- docs: 文档更新
- style: 代码格式调整
- refactor: 重构
- test: 测试相关
- chore: 构建/工具链相关

## 常见问题

### 服务启动失败

1. 检查Nacos是否正常运行
2. 检查MySQL、Redis等基础服务是否启动
3. 检查端口是否被占用
4. 查看日志文件排查具体错误

### Nacos配置问题

1. 确保配置的命名空间正确
2. 检查共享配置是否已创建
3. 验证配置格式是否正确（YAML）

## 贡献指南

欢迎贡献代码、提交问题和建议！

1. Fork 本项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 许可证

本项目采用 MIT 许可证。详见 [LICENSE](LICENSE) 文件。

## 联系方式

- 维护者：传智教育·研究院
- 邮箱：zhanghuyi@itcast.cn
- 组织：研究院研发组

## 致谢

感谢以下开源项目：

- Spring Boot & Spring Cloud
- Alibaba Nacos、Sentinel、Seata
- MyBatis Plus
- Hutool
- 以及所有依赖的开源组件

---

⭐ 如果这个项目对你有帮助，请给个Star支持一下！
