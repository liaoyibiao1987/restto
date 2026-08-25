## 1. Project Overview
- 项目类型：基于go和springboot的分布式服务器数据备份系统
- go在client端实现，springboot在manager端实现，netty作为通信框架
- go实现纯静态编译（CGO_ENABLED=0），各个项目实现独立，通过命令行参数传递功能模块名称，服务端根据功能模块名称调用对应的功能模块
  - processor目录存放各个功能模块的代码（Go module：cmd + internal）
  - manager/web目录存放springboot的代码
  - manager/client目录存放vue的前端代码
- 核心技术栈：go + vue + springboot + netty + mysql
- 项目用途：服务器数据备份系统
- 核心约束：所有接口需加 Token 权限校验、遵循 RESTful 风格、数据库变化时需同步更新 migrations 目录下的迁移脚本
- 每个单独项目结构：
  - logs：日志目录
  - .env：环境变量文件
  - README.md：项目说明文件
## 2. Setup & Development
### 2.1 开发运行
- 基于wsl2的ubuntu系统中开发，使用wsl系统的node和go以及java8、maven环境
- 客户端业务采用go编写，将业务代码打包为二进制文件，通过netty传输到服务端
- 客户端将功能切割，实现符合AI CLI的架构，每个功能模块独立，通过命令行参数传递功能模块名称，服务端根据功能模块名称调用对应的功能模块

### 2.3 数据库配置
- 数据库类型：MySQL 8.0以上
- 禁止操作：不得删除 migrations/ 目录下的历史迁移文件

## 3. Testing Guidelines
- 测试要求：
  - 新增代码测试覆盖率≥80%
  - 禁止行为：不得跳过必填测试用例、不得修改测试预期结果

## 4. Code Style
### 4.1 通用规则
- 代码格式化工具：Prettier + ESLint
- 代码注释：遵循 JSDoc 规范，注释需包含函数功能、参数说明、返回值说明
- 代码风格：遵循 Airbnb JavaScript 风格指南
- 代码命名：使用驼峰命名法，如 getUserInfo
- 代码长度：单行代码长度不超过280字符，函数体不超过120行
- 代码复用：禁止重复代码，使用函数、类、模块等方式封装复用逻辑

# 5. 操作边界与禁止行为
### 5.1 禁止操作
不得修改/删除：.env（密钥文件）、src/core（核心工具类）、migrations/（迁移文件）
不得修改：package.json 中的依赖版本、CI/CD 流水线配置（.github/workflows/）
不得提交：node_modules 目录、log目录、IDE 配置文件（.vscode/）、未完成的测试代码

### 5.2 操作边界
修改权限校验、数据加密等核心逻辑前，必须生成变更方案并标记“需人类审核”
新增依赖需先检查现有依赖，禁止重复引入同类库（如已有 Axios 则不引入 Fetch API 封装库）

## 6. Optional Sections（可选模块）
### 6.1 安全规范（生产项目必选）
- 密钥管理：所有密钥存于 .env 文件，不得硬编码到代码中
- 安全校验：接口必须做参数校验，禁止直接接收前端传入的 SQL 语句

### 6.2 调试与排错
- 日志位置：logs/ 目录，按日期分区（如 logs/2026-03-12.log）
