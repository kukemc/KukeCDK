# KukeCDK - 新一代的轻量化 CDK 插件

[English README](./README.en.md)

![bStats](https://bstats.org/signatures/bukkit/KukeCDK.svg)

## 功能
- 支持执行多条指令（管道符 `|` 分割）
- 支持一次性 CDK 与可多次使用 CDK
- 支持自定义 CDK 名称与有效期
- 支持批量生成、批量删除、批量导出
- 支持记录所有 CDK 使用日志
- 支持一键查看所有已生成的 CDK
- 支持创建每名玩家都可兑换一次的 CDK
- 兼容 Folia 服务端
- 支持 YAML、MySQL、SQLite 数据存储
- 支持 铁砧 GUI 输入 CDK（`/cdk anvil`）
- 支持 PlaceholderAPI 变量
- 支持可选 HTTP API 服务，内置详细 API 文档页面

## 指令与权限
| 指令 | 介绍 | 权限 |
| --- | --- | --- |
| `/cdk use <CDK>` | 使用 CDK | `kukecdk.use` |
| `/cdk verify <CDK>` | 验证 CDK 可用性（不消耗） | `kukecdk.use` |
| `/cdk anvil` | 打开铁砧兑换界面 | `kukecdk.use` |
| `/cdk create single <id> <数量> "<命令1\|命令2\|...>" [有效时间]` | 批量创建一次性 CDK | `kukecdk.admin.create` |
| `/cdk create multiple <name\|random> <id> <数量> "<命令1\|命令2\|...>" [有效时间]` | 创建可重复使用 CDK | `kukecdk.admin.create` |
| `/cdk add <id> <数量>` | 根据 ID 批量生成/增加使用次数 | `kukecdk.admin.add` |
| `/cdk delete cdk <CDK名称>` | 删除指定 CDK | `kukecdk.admin.delete` |
| `/cdk delete id <id>` | 删除此 id 下的所有 CDK | `kukecdk.admin.delete` |
| `/cdk list` | 查看所有 CDK | `kukecdk.admin.list` |
| `/cdk export` | 导出 CDK 配置和日志 | `kukecdk.admin.export` |
| `/cdk reload` | 重新加载配置和语言文件 | `kukecdk.admin.reload` |
| `/cdk migrate <yaml\|sqlite\|mysql> <yaml\|sqlite\|mysql> [confirm]` | 在存储模式之间迁移数据 | `kukecdk.admin.migrate` |
| `/cdk help` | 显示帮助信息 | — |

> 提示：`create` 指令的命令参数需使用双引号括住，整段会被视为一个参数；内部使用管道符 `|` 分割多条命令。

## 使用示例

```bash
# 创建五个 ID 为“钻石”、过期时间为 2024-12-01 10:00 的随机一次性 CDK
/cdk create single 钻石 5 "give %player% diamond 1" 2024-12-01 10:00

# 创建名字为“vip666”、ID 为“黄金”，可兑换次数 999、过期时间为 2024-12-01 10:00 的多次性 CDK
/cdk create multiple vip666 黄金 999 "give %player% gold 10" 2024-12-01 10:00

# 根据 ID 批量生成或增加次数
/cdk add 钻石 10
/cdk add 黄金 10

# 执行多条指令（使用管道符分隔）
/cdk create single 测试 3 "give %player% diamond 1|bc %player%兑换了CDK" 2024-12-01 10:00

# 打开铁砧 GUI 进行 CDK 兑换
/cdk anvil
```

## config.yml 配置文件

```yaml
# KukeCDK 插件配置文件

# 插件版本(请勿修改)
plugin_version: "1.0"

# 语言设置 (可用: zh_CN, en_US)
language: "zh_CN"

# 存储模式 (可用: yaml, sqlite, mysql)
storage_mode: "yaml"

# MySQL 配置 (仅在 storage_mode 设置为 mysql 时有效)
mysql:
  host: "localhost"
  port: 3306
  database: "kukecdk"
  username: "root"
  password: "password"
  table_prefix: "kukecdk_"

# 默认生成的 CDK 字符库
default_cdk_characters: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

# 默认生成的 CDK 名称长度
default_cdk_name_length: 8

# 失败尝试配置
failed_attempts:
  enabled: true          # 是否启用失败尝试限制
  max_attempts: 3        # 允许的最大失败尝试次数
  ban_duration: 10       # 禁止时长（单位：分钟）
  reset_duration: 10     # 失败尝试记录重置时间（单位：分钟）

# 铁砧GUI设置（标题与物品文案支持PAPI变量与%player%）
anvil_gui:
  title: "&bCDK兑换 - &f输入你的CDK"
  input_item_lore:
    - "&7在上方输入你的CDK"
```

## HTTP API 服务

KukeCDK 支持内置 HTTP API 服务，默认关闭。启用后可以通过 REST API 管理、查询、验证和兑换 CDK，并在本地提供详细文档页面。

默认文档地址：

```text
http://127.0.0.1:8765/docs
```

基础配置：

```yaml
api:
  enabled: false
  host: "127.0.0.1"
  port: 8765
  base_path: "/api/v1"
  docs_path: "/docs"
  auth:
    enabled: true
    tokens:
      - name: "admin"
        token: "change-me-admin-token"
        scopes:
          - "*"
```

鉴权方式：

```http
Authorization: Bearer change-me-admin-token
```

常用接口：

| 接口 | 说明 | Scope |
| --- | --- | --- |
| `GET /api/v1/health` | 健康检查 | `server:read` |
| `GET /api/v1/stats` | CDK 统计 | `server:read` |
| `GET /api/v1/cdks` | CDK 列表 | `cdk:read` |
| `GET /api/v1/cdks/{name}` | 查询指定 CDK | `cdk:read` |
| `POST /api/v1/cdks` | 创建 CDK | `cdk:create` |
| `PATCH /api/v1/cdks/{name}` | 修改 CDK | `cdk:update` |
| `DELETE /api/v1/cdks/{name}` | 删除 CDK | `cdk:delete` |
| `POST /api/v1/cdks/{name}/verify` | 验证 CDK | `cdk:verify` |
| `POST /api/v1/cdks/{name}/redeem` | 为在线玩家兑换 CDK | `cdk:redeem` |
| `GET /api/v1/logs` | 查询兑换日志 | `log:read` |
| `POST /api/v1/admin/reload` | 重载插件配置并重启 API | `admin:reload` |

安全建议：保持 `host: "127.0.0.1"`，使用 Nginx/Caddy 反代 HTTPS 对外提供访问，并务必修改默认 token。

## 插件截图
![插件截图](https://github.com/user-attachments/assets/83408843-b970-4474-8637-5865eaba800d)

## 反馈
- QQ 讨论群：`981954623`
- GitHub：`https://github.com/kukemc/KukeCDK`
