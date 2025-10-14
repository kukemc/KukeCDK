# KukeCDK - A Lightweight Next‑Gen CDK Plugin

[中文 README](./README.md)

![bStats](https://bstats.org/signatures/bukkit/KukeCDK.svg)

## Features
- Execute multiple commands (split by `|`)
- Single-use and multi-use CDKs
- Custom CDK name and expiration
- Batch generate, delete, and export
- Log every CDK redemption
- View all generated CDKs at a glance
- Create CDKs redeemable once per player
- Folia-compatible (periodic tasks are disabled)
- YAML, MySQL, and SQLite storage
- Anvil GUI input for CDK (`/cdk anvil`)

## Commands & Permissions
| Command | Description | Permission |
| --- | --- | --- |
| `/cdk use <CDK>` | Use a CDK | `kukecdk.use` |
| `/cdk verify <CDK>` | Verify CDK availability (no consumption) | `kukecdk.use` |
| `/cdk anvil` | Open the anvil redemption GUI | `kukecdk.use` |
| `/cdk create single <id> <quantity> "<cmd1|cmd2|...>" [expiration]` | Create single-use CDKs in batch | `kukecdk.admin.create` |
| `/cdk create multiple <name|random> <id> <quantity> "<cmd1|cmd2|...>" [expiration]` | Create reusable CDKs | `kukecdk.admin.create` |
| `/cdk add <id> <quantity>` | Batch generate/increase usage by ID | `kukecdk.admin.add` |
| `/cdk delete cdk <name>` | Delete a CDK | `kukecdk.admin.delete` |
| `/cdk delete id <id>` | Delete all CDKs under the id | `kukecdk.admin.delete` |
| `/cdk list` | List all CDKs | `kukecdk.admin.list` |
| `/cdk export` | Export CDKs and logs | `kukecdk.admin.export` |
| `/cdk reload` | Reload config and language files | `kukecdk.admin.reload` |
| `/cdk migrate <yaml|sqlite|mysql> <yaml|sqlite|mysql> [confirm]` | Migrate between storage modes | `kukecdk.admin.migrate` |
| `/cdk help` | Show help | — |

> Tip: In `create` commands, wrap the command string in double quotes to treat it as a single argument. Use `|` inside to separate multiple commands.

## Usage Examples

```bash
# Create 5 random single-use CDKs under id "钻石" expiring at 2024-12-01 10:00
/cdk create single 钻石 5 "give %player% diamond 1" 2024-12-01 10:00

# Create reusable CDK named "vip666" under id "黄金", 999 uses, expires at 2024-12-01 10:00
/cdk create multiple vip666 黄金 999 "give %player% gold 10" 2024-12-01 10:00

# Batch generate/increase usage
/cdk add 钻石 10
/cdk add 黄金 10

# Execute multiple commands (split by |)
/cdk create single 测试 3 "give %player% diamond 1|bc %player%兑换了CDK" 2024-12-01 10:00

# Open the Anvil GUI for redemption
/cdk anvil
```

## config.yml

```yaml
# KukeCDK plugin config

plugin_version: "1.0"           # Do not modify

language: "zh_CN"                # Available: zh_CN, en_US

storage_mode: "yaml"             # Available: yaml, sqlite, mysql

mysql:
  host: "localhost"
  port: 3306
  database: "kukecdk"
  username: "root"
  password: "password"
  table_prefix: "kukecdk_"

default_cdk_characters: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

default_cdk_name_length: 8

failed_attempts:
  enabled: true
  max_attempts: 3
  ban_duration: 10
  reset_duration: 10

anvil_gui:
  title: "&bCDK兑换 - &f输入你的CDK"
  input_item_lore:
    - "&7在上方输入你的CDK"
```

## Feedback
- QQ Group: `981954623`
- GitHub: `https://github.com/kukemc/KukeCDK`