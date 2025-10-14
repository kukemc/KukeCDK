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
- Folia-compatible
- YAML, MySQL, and SQLite storage
- Anvil GUI input for CDK (`/cdk anvil`)

## Commands & Permissions
| Command | Description | Permission |
| --- | --- | --- |
| `/cdk use <CDK>` | Use a CDK | `kukecdk.use` |
| `/cdk verify <CDK>` | Verify CDK availability (no consumption) | `kukecdk.use` |
| `/cdk anvil` | Open the anvil redemption GUI | `kukecdk.use` |
| `/cdk create single <id> <quantity> "<cmd1\|cmd2\|...>" [expiration]` | Create single-use CDKs in batch | `kukecdk.admin.create` |
| `/cdk create multiple <name\|random> <id> <quantity> "<cmd1\|cmd2\|...>" [expiration]` | Create reusable CDKs | `kukecdk.admin.create` |
| `/cdk add <id> <quantity>` | Batch generate/increase usage by ID | `kukecdk.admin.add` |
| `/cdk delete cdk <name>` | Delete a CDK | `kukecdk.admin.delete` |
| `/cdk delete id <id>` | Delete all CDKs under the id | `kukecdk.admin.delete` |
| `/cdk list` | List all CDKs | `kukecdk.admin.list` |
| `/cdk export` | Export CDKs and logs | `kukecdk.admin.export` |
| `/cdk reload` | Reload config and language files | `kukecdk.admin.reload` |
| `/cdk migrate <yaml\|sqlite\|mysql> <yaml\|sqlite\|mysql> [confirm]` | Migrate between storage modes | `kukecdk.admin.migrate` |
| `/cdk help` | Show help | — |

> Tip: In `create` commands, wrap the command string in double quotes to treat it as a single argument. Use `|` inside to separate multiple commands.

## Usage Examples

```bash
# Create 5 random single-use CDKs under id "diamond" expiring at 2024-12-01 10:00
/cdk create single diamond 5 "give %player% diamond 1" 2024-12-01 10:00

# Create reusable CDK named "vip123" under id "gold", 999 uses, expires at 2024-12-01 10:00
/cdk create multiple vip123 gold 999 "give %player% gold 10" 2024-12-01 10:00

# Batch generate/increase usage
/cdk add diamond 10
/cdk add gold 10

# Execute multiple commands (split by |)
/cdk create single test 3 "give %player% diamond 1|bc %player% used a CDK" 2024-12-01 10:00

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
  title: "&bCDK Redemption - &fEnter your CDK"
  input_item_lore:
    - "&7Enter your CDK above"
```

## plugin screenshot
![plugin screenshot](https://github.com/user-attachments/assets/83408843-b970-4474-8637-5865eaba800d)

## Feedback
- QQ Group: `981954623`
- GitHub: `https://github.com/kukemc/KukeCDK`