# KukeCDK - 新一代的轻量化CDK插件

## 功能
- **支持 执行多条指令**
- **支持 一次性CDK**
- **支持 可多次使用CDK**
- **支持 自定义CDK名称**
- **支持 设置CDK有效时长**
- **支持 批量生成 / 批量删除 / 批量导出**
- **支持 记录所有CDK使用日志**
- **支持 一键查看所有已生成的CDK**
- **支持 创建每名玩家都可兑换一次的CDK**

## 指令与权限
| 指令 | 介绍 | 权限 |
| --- | --- | --- |
| `/cdk use <CDK>` | 使用 CDK | `kukecdk.use` |
| `/cdk create single <id> <数量> "<命令1|命令2|...>" [有效时间]` | 批量创建一次性CDK | `kukecdk.admin.create` |
| `/cdk create multiple <name|random> <id> <数量> "<命令1|命令2|...>" [有效时间]` | 创建每名玩家都可使用的多次性CDK | `kukecdk.admin.create` |
| `/cdk add <id> <数量>` | 根据 ID 批量生成/增加使用次数 | `kukecdk.admin.add` |
| `/cdk delete cdk <CDK名称>` | 删除 CDK | `kukecdk.admin.delete` |
| `/cdk delete id <id>` | 删除此 id 下的所有 CDK | `kukecdk.admin.delete` |
| `/cdk list` | 查看所有 CDK | `kukecdk.admin.list` |
| `/cdk export` | 导出 CDK 配置和日志 | `kukecdk.admin.export` |
| `/cdk reload` | 重新加载 CDK 配置 | `kukecdk.admin.reload` |
| `/cdk help` | 显示帮助信息 | |

## 使用示例:
| 示例指令 | 介绍 |
| --- | --- |
| `/cdk create single 钻石 5 "give %player% diamond 1" 2024-12-01 10:00` | 创建五个ID为"钻石"过期时间为2024-12-01 10:00的随机CDK |
| `/cdk create multiple vip666 黄金 999 "give %player% gold 10" 2024-12-01 10:00` | 创建名字为"vip666" ID为"黄金" 可兑换次数为999 过期时间为2024-12-01 10:00 每人都可兑换一次的CDK 直到次数用完为止 |
| `/cdk add 钻石 10` | 按照ID"钻石"再生成10个CDK |
| `/cdk add 黄金 10` | 按照ID"黄金"增加十次可兑换次数 |
| `/cdk create single 测试 3 "give %player% diamond 1|bc %player%兑换了CDK" 2024-12-01 10:00` | 执行多条指令 |

## config.yml 配置文件
```yaml
# KukeCDK 插件配置文件

# 插件版本(请勿修改)
plugin_version: "1.0"

# 默认生成的 CDK 字符库
default_cdk_characters: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

# 默认生成的 CDK 名称长度
default_cdk_name_length: 8
```

## 插件截图
![image](https://github.com/user-attachments/assets/a67672a5-4fd3-469f-b8fd-d9a583ebaea7)

## 反馈
QQ讨论群: 981954623

## 统计
![统计](https://bstats.org/signatures/bukkit/KukeCDK.svg)
