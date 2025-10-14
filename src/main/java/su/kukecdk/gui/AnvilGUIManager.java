package su.kukecdk.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import su.kukecdk.command.CDKCommandHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简易Anvil GUI管理器（兼容1.12），用于输入CDK并立即使用。
 */
public class AnvilGUIManager implements Listener {
    private final JavaPlugin plugin;
    private final FileConfiguration config;
    private final CDKCommandHandler commandHandler;
    private final Set<UUID> activePlayers = Collections.synchronizedSet(new HashSet<>());
    private final Set<UUID> fallbackPlayers = Collections.synchronizedSet(new HashSet<>());
    private final Map<UUID, String> lastRenameText = new ConcurrentHashMap<>();

    public AnvilGUIManager(JavaPlugin plugin, FileConfiguration config, CDKCommandHandler commandHandler) {
        this.plugin = plugin;
        this.config = config;
        this.commandHandler = commandHandler;
    }

    public void openAnvil(Player player) {
        String titleMsg = parsePlaceholders(player, config.getString("anvil_gui.title", "&bCDK兑换 - 在此输入"));
        // 默认左槽物品名设置为一个空格，避免显示物品默认名称
        ItemStack leftPane = createPane(true, " ");

        // 优先尝试调用新版 AnvilGUI（通过反射，兼容 1.21+），失败则降级提示指令输入
        boolean opened = openWithModernAnvilGUIReflect(player, titleMsg, leftPane);
        if (!opened) {
            // 低版本或未加载库，使用原版铁砧回退方案（1.8-1.12）
            openAnvilFallback(player, leftPane);
        }
    }

    private ItemStack createPane(boolean green, String displayName) {
        ItemStack item;
        // 优先使用新版本材质名
        try {
            Material modern = Material.valueOf(green ? "GREEN_STAINED_GLASS_PANE" : "RED_STAINED_GLASS_PANE");
            item = new ItemStack(modern, 1);
        } catch (Throwable ignored) {
            // 回退到旧版本材质名与数据值
            try {
                Material legacy = Material.valueOf("STAINED_GLASS_PANE");
                item = new ItemStack(legacy, 1, (short) (green ? 13 : 14));
            } catch (Throwable e) {
                item = new ItemStack(Material.PAPER, 1);
            }
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName == null ? " " : displayName);
            meta.setLore(null);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 通过反射调用新版/旧版 AnvilGUI Builder，避免在低 JRE 加载高版本类导致崩溃。
     * 成功打开返回 true，否则返回 false。
     */
    private boolean openWithModernAnvilGUIReflect(Player player, String title, ItemStack leftPane) {
        try {
            // 直接使用 net.wesjd.anvilgui（未重定位）
            Class<?> builderCls = Class.forName("net.wesjd.anvilgui.AnvilGUI$Builder");
            Object builder = builderCls.getConstructor().newInstance();

            // builder.plugin(plugin)
            builderCls.getMethod("plugin", org.bukkit.plugin.Plugin.class).invoke(builder, plugin);
            // builder.itemLeft(leftPane)
            builderCls.getMethod("itemLeft", ItemStack.class).invoke(builder, leftPane);
            // builder.title(title)（若存在）
            try {
                builderCls.getMethod("title", String.class).invoke(builder, color(title));
            } catch (NoSuchMethodException ignored) {}
            // builder.text(" ") 初始填充一个空格，避免显示物品默认名称
            builderCls.getMethod("text", String.class).invoke(builder, " ");

            // onClose 适配新版（Consumer<StateSnapshot>）与旧版（Consumer<Player>）
            try {
                Class<?> stateSnapshotCls = Class.forName("net.wesjd.anvilgui.AnvilGUI$StateSnapshot");
                java.util.function.Consumer<Object> onClose = snapshot -> {
                    try {
                        Player p = (Player) stateSnapshotCls.getMethod("getPlayer").invoke(snapshot);
                        activePlayers.remove(p.getUniqueId());
                        lastRenameText.remove(p.getUniqueId());
                    } catch (Throwable ignored) {}
                };
                builderCls.getMethod("onClose", java.util.function.Consumer.class).invoke(builder, onClose);
            } catch (ClassNotFoundException e) {
                java.util.function.Consumer<Player> onClose = p -> {
                    activePlayers.remove(p.getUniqueId());
                    lastRenameText.remove(p.getUniqueId());
                };
                builderCls.getMethod("onClose", java.util.function.Consumer.class).invoke(builder, onClose);
            }

            // 优先尝试新版 onClick(BiFunction<Integer, StateSnapshot, List<ResponseAction>>)
            try {
                Class<?> stateSnapshotCls = Class.forName("net.wesjd.anvilgui.AnvilGUI$StateSnapshot");
                Class<?> responseActionCls = Class.forName("net.wesjd.anvilgui.AnvilGUI$ResponseAction");
                java.lang.reflect.Method closeAction = responseActionCls.getMethod("close");
                java.lang.reflect.Method replaceInputText = null;
                try { replaceInputText = responseActionCls.getMethod("replaceInputText", String.class); } catch (NoSuchMethodException ignored) {}
                // 尝试可用的UI更新方法（若存在）
                java.lang.reflect.Method setLeftItemMeth = null;
                try { setLeftItemMeth = responseActionCls.getMethod("setLeftItem", ItemStack.class); } catch (NoSuchMethodException ignored) {}
                final java.lang.reflect.Method replaceInputTextMeth = replaceInputText;
                final java.lang.reflect.Method setLeftItem = setLeftItemMeth;

                java.util.function.BiFunction<Integer, Object, java.util.List<?>> onClick = (slot, snapshot) -> {
                    // 仅在输出槽（索引 2）触发
                    if (slot != 2) return java.util.Collections.emptyList();
                    try {
                        String input = (String) stateSnapshotCls.getMethod("getText").invoke(snapshot);
                        String cdkText = stripColor(input == null ? "" : input).trim();
                        if (cdkText.isEmpty()) {
                            Player p = (Player) stateSnapshotCls.getMethod("getPlayer").invoke(snapshot);
                            p.sendMessage(color("&c请输入CDK内容再兑换"));
                            java.util.List<Object> actions = new java.util.ArrayList<>();
                            // 保持输入框为一个空格，避免显示默认物品名
                            if (replaceInputTextMeth != null) {
                                try { actions.add(replaceInputTextMeth.invoke(null, " ")); } catch (Throwable ignore) {}
                            }
                            // 若支持更新左侧物品，重新设置其显示名为空格
                            if (setLeftItem != null) {
                                try { actions.add(setLeftItem.invoke(null, createPane(true, " "))); } catch (Throwable ignore) {}
                            }
                            return actions.isEmpty() ? java.util.Collections.emptyList() : actions;
                        }
                        Player p = (Player) stateSnapshotCls.getMethod("getPlayer").invoke(snapshot);
                        activePlayers.remove(p.getUniqueId());
                        lastRenameText.remove(p.getUniqueId());
                        useCDK(p, cdkText);
                        return java.util.Collections.singletonList(closeAction.invoke(null));
                    } catch (Throwable t) {
                        plugin.getLogger().warning("AnvilGUI onClick 处理失败: " + t.getMessage());
                        try { return java.util.Collections.singletonList(closeAction.invoke(null)); } catch (Throwable ignore) {}
                        return java.util.Collections.emptyList();
                    }
                };
                builderCls.getMethod("onClick", java.util.function.BiFunction.class).invoke(builder, onClick);
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                // 旧版 onComplete(BiFunction<Player, String, Response>) 兜底
                try {
                    Class<?> responseCls = Class.forName("net.wesjd.anvilgui.AnvilGUI$Response");
                    java.lang.reflect.Method closeResp = responseCls.getMethod("close");
                    java.lang.reflect.Method textResp = responseCls.getMethod("text", String.class);
                    java.util.function.BiFunction<Object, Object, Object> onComplete = (pObj, inputObj) -> {
                        Player p = (Player) pObj;
                        String input = (String) inputObj;
                        String cdkText = stripColor(input == null ? "" : input).trim();
                        if (cdkText.isEmpty()) {
                            p.sendMessage(color("&c请输入CDK内容再兑换"));
                            try { return textResp.invoke(null, color("&c请输入CDK")); } catch (Throwable ignore) {}
                            return null;
                        }
                        activePlayers.remove(p.getUniqueId());
                        lastRenameText.remove(p.getUniqueId());
                        useCDK(p, cdkText);
                        try { return closeResp.invoke(null); } catch (Throwable ignore) {}
                        return null;
                    };
                    builderCls.getMethod("onComplete", java.util.function.BiFunction.class).invoke(builder, onComplete);
                } catch (Throwable failOld) {
                    plugin.getLogger().warning("AnvilGUI 旧版 onComplete 适配失败: " + failOld.getMessage());
                    return false;
                }
            }

            // builder.open(player)
            builderCls.getMethod("open", Player.class).invoke(builder, player);
            return true;
        } catch (ClassNotFoundException | UnsupportedClassVersionError e) {
            // 类不存在或当前 JRE 无法加载（例如 Java 8）
            return false;
        } catch (Throwable t) {
            plugin.getLogger().warning("AnvilGUI 反射调用异常: " + t.getMessage());
            return false;
        }
    }

    // ================= 回退：原版铁砧（1.8-1.12） =================
    private void openAnvilFallback(Player player, ItemStack leftPane) {
        try {
            Inventory inv = Bukkit.createInventory(player, InventoryType.ANVIL);
            inv.setItem(0, leftPane);
            player.openInventory(inv);
            // 初始打开时强制清空结果槽，避免显示物品默认名称
            try {
                inv.setItem(2, null);
            } catch (Throwable ignored) {}
            fallbackPlayers.add(player.getUniqueId());
        } catch (Throwable t) {
            player.sendMessage(color("&c无法打开铁砧输入界面，请使用指令：/cdk use <CDK>"));
        }
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        try {
            Player player = (Player) event.getView().getPlayer();
            if (!fallbackPlayers.contains(player.getUniqueId())) return;
            ItemStack result = event.getResult();
            String rename = "";
            if (result != null) {
                ItemMeta meta = result.getItemMeta();
                if (meta != null && meta.hasDisplayName()) {
                    rename = meta.getDisplayName();
                }
            }
            String trimmed = stripColor(rename).trim();
            if (!rename.isEmpty()) {
                lastRenameText.put(player.getUniqueId(), stripColor(rename));
            } else {
                // 若没有自定义名称，则清空缓存
                lastRenameText.remove(player.getUniqueId());
            }
            // 当输入为空或仅为空格时，清空输出槽，避免显示物品默认名称
            if (trimmed.isEmpty()) {
                event.setResult(null);
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler
    public void onAnvilClick(InventoryClickEvent event) {
        try {
            if (event.getInventory() == null || event.getInventory().getType() != InventoryType.ANVIL) return;
            if (!(event.getWhoClicked() instanceof Player)) return;
            Player player = (Player) event.getWhoClicked();
            if (!fallbackPlayers.contains(player.getUniqueId())) return;
            // 输出槽位索引为 2
            if (event.getRawSlot() == 2) {
                event.setCancelled(true);
                String input = lastRenameText.getOrDefault(player.getUniqueId(), "").trim();
                if (input.isEmpty()) {
                    // 清空当前结果槽，避免默认物品名
                    try { event.setCurrentItem(null); } catch (Throwable ignored) {}
                    player.sendMessage(color("&c请输入CDK内容再兑换"));
                    return;
                }
                useCDK(player, input);
                fallbackPlayers.remove(player.getUniqueId());
                lastRenameText.remove(player.getUniqueId());
                player.closeInventory();
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler
    public void onAnvilClose(InventoryCloseEvent event) {
        try {
            if (event.getInventory() == null || event.getInventory().getType() != InventoryType.ANVIL) return;
            if (!(event.getPlayer() instanceof Player)) return;
            Player player = (Player) event.getPlayer();
            if (!fallbackPlayers.contains(player.getUniqueId())) return;
            fallbackPlayers.remove(player.getUniqueId());
            lastRenameText.remove(player.getUniqueId());
        } catch (Throwable ignored) {}
    }

    // 使用 AnvilGUI 库后，不再需要拦截原版铁砧事件，避免冲突

    private void useCDK(Player player, String cdkName) {
        // 构造类似 /cdk use <code> 的调用路径
        String[] args = new String[] {"use", cdkName};
        commandHandler.handleUseCommand(player, args);
    }

    private String parsePlaceholders(Player player, String text) {
        if (text == null) return "";
        String withBuiltin = text;
        // 当player为null时，不应触发NPE；将%player%替换为空或保留原样
        if (withBuiltin.contains("%player%")) {
            withBuiltin = withBuiltin.replace("%player%", player != null ? player.getName() : "");
        }
        try {
            org.bukkit.plugin.Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
            if (papi != null) {
                try {
                    Class<?> cls = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                    java.lang.reflect.Method m = cls.getMethod("setPlaceholders", Player.class, String.class);
                    // 仅当player非空时尝试PAPI解析；否则跳过避免NPE
                    Object out = player != null ? m.invoke(null, player, withBuiltin) : withBuiltin;
                    return out instanceof String ? (String) out : withBuiltin;
                } catch (Throwable ignore) {
                    return withBuiltin;
                }
            }
        } catch (Throwable ignored) {}
        return withBuiltin;
    }

    private String color(String s) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', s);
    }
    private List<String> color(List<String> list) {
        List<String> out = new ArrayList<>();
        for (String l : list) out.add(color(l));
        return out;
    }
    private String stripColor(String s) {
        return org.bukkit.ChatColor.stripColor(s);
    }

    // 不需要 getRenameTextSafe；交由 AnvilGUI 处理输入文本
}