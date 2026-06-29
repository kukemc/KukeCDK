package su.kukecdk.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import su.kukecdk.util.FoliaSupport;
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
    private final Map<UUID, Object> overwriteTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> overwriteTickCounts = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> rightHasInputState = new ConcurrentHashMap<>();
    private static final String RED_PROMPT_NAME = "请输入CDK";

    public AnvilGUIManager(JavaPlugin plugin, FileConfiguration config, CDKCommandHandler commandHandler) {
        this.plugin = plugin;
        this.config = config;
        this.commandHandler = commandHandler;
    }

    public void openAnvil(Player player) {
        String titleMsg = parsePlaceholders(player, config.getString("anvil_gui.title", "&bCDK兑换 - 在此输入"));
        // 左侧使用纸张作为输入指示
        ItemStack leftPane = createInputPaper(player);

        // 优先尝试调用新版 AnvilGUI（通过反射，兼容 1.21+），失败则降级提示指令输入
        boolean opened = openWithModernAnvilGUIReflect(player, titleMsg, leftPane);
        if (!opened) {
            // 低版本或未加载库，使用原版铁砧回退方案（1.8-1.12）
            openAnvilFallback(player, leftPane);
        }
    }

    private ItemStack createPane(boolean green, String displayName) {
        return createPane(green, displayName, null);
    }

    private ItemStack createPane(boolean green, String displayName, List<String> lore) {
        ItemStack item = buildItemByConfig(green);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName == null ? " " : displayName);
            if (lore != null && !lore.isEmpty()) meta.setLore(lore);
            // CustomModelData（若配置存在且版本支持）
            applyCustomModelData(meta, getOutputCMD(green));
            item.setItemMeta(meta);
        }
        return item;
    }

    // 左侧输入纸张：材质、名称、lore 可配置，支持PAPI
    private ItemStack createInputPaper(Player player) {
        String matToken = config.getString("anvil_gui.input_item_material", "PAPER");
        ItemStack paper = parseMaterialToken(matToken, false);
        try {
            ItemMeta lm = paper.getItemMeta();
            if (lm != null) {
                String inputName = config.getString("anvil_gui.input_item_name", " ");
                lm.setDisplayName(color(parsePlaceholders(player, inputName)));
                List<String> inputLore = color(parsePlaceholdersList(player, config.getStringList("anvil_gui.input_item_lore")));
                if (inputLore != null && !inputLore.isEmpty()) lm.setLore(inputLore);
                applyCustomModelData(lm, getCustomModelData("anvil_gui.input_item_custom_model_data"));
                paper.setItemMeta(lm);
            }
        } catch (Throwable ignored) {}
        return paper;
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
            // 能力探测：是否支持设置输出槽或在响应中更新输出槽
            boolean builderSupportsOutput = false;
            try {
                builderCls.getMethod("itemOutput", ItemStack.class);
                builderSupportsOutput = true;
            } catch (NoSuchMethodException ignored) {}

            // onClose 适配新版（Consumer<StateSnapshot>）与旧版（Consumer<Player>）
            try {
                Class<?> stateSnapshotCls = Class.forName("net.wesjd.anvilgui.AnvilGUI$StateSnapshot");
                java.util.function.Consumer<Object> onClose = snapshot -> {
                    try {
                        Player p = (Player) stateSnapshotCls.getMethod("getPlayer").invoke(snapshot);
                        activePlayers.remove(p.getUniqueId());
                        lastRenameText.remove(p.getUniqueId());
                        stopOverwriteTask(p);
                    } catch (Throwable ignored) {}
                };
                builderCls.getMethod("onClose", java.util.function.Consumer.class).invoke(builder, onClose);
            } catch (ClassNotFoundException e) {
                java.util.function.Consumer<Player> onClose = p -> {
                    activePlayers.remove(p.getUniqueId());
                    lastRenameText.remove(p.getUniqueId());
                    stopOverwriteTask(p);
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
                // 输出槽更新（不同版本命名可能不同）
                java.lang.reflect.Method setItemOutputMeth = null;
                try { setItemOutputMeth = responseActionCls.getMethod("setItemOutput", ItemStack.class); } catch (NoSuchMethodException ignored) {}
                java.lang.reflect.Method setOutputItemMeth = null;
                try { setOutputItemMeth = responseActionCls.getMethod("setOutputItem", ItemStack.class); } catch (NoSuchMethodException ignored) {}
                final java.lang.reflect.Method replaceInputTextMeth = replaceInputText;
                final java.lang.reflect.Method setLeftItem = setLeftItemMeth;
                final java.lang.reflect.Method setItemOutput = setItemOutputMeth != null ? setItemOutputMeth : setOutputItemMeth;

                // 若既不支持 builder.itemOutput 也不支持响应更新输出槽，则回退到原版铁砧
                if (!builderSupportsOutput && setItemOutput == null) {
                    return false;
                }

                java.util.function.BiFunction<Integer, Object, java.util.List<?>> onClick = (slot, snapshot) -> {
                    try {
                        String input = (String) stateSnapshotCls.getMethod("getText").invoke(snapshot);
                        // 兜底：如上面拿到的文本为空，则尝试从铁砧容器读取重命名文本
                        if (input == null || input.trim().isEmpty()) {
                            try {
                                Player p = (Player) stateSnapshotCls.getMethod("getPlayer").invoke(snapshot);
                                Inventory top = p.getOpenInventory().getTopInventory();
                                if (top instanceof AnvilInventory) {
                                    String rt = ((AnvilInventory) top).getRenameText();
                                    if (rt != null) input = rt;
                                }
                            } catch (Throwable ignored) {}
                        }
                        String cdkText = stripColor(input == null ? "" : input).trim();
                        java.util.List<Object> actions = new java.util.ArrayList<>();
                // 改为根据右侧玻璃的显示名来判断是否有输入：有名称则视为已输入
                if (setItemOutput != null) {
                    try {
                        Player p = (Player) stateSnapshotCls.getMethod("getPlayer").invoke(snapshot);
                        Inventory top = p.getOpenInventory().getTopInventory();
                        String nameToKeep = "";
                        if (top != null && top.getType() == InventoryType.ANVIL) {
                            ItemStack cur = top.getItem(2);
                            ItemMeta cm = cur != null ? cur.getItemMeta() : null;
                            if (cm != null && cm.hasDisplayName()) nameToKeep = cm.getDisplayName();
                        }
                        String trimmedName = stripColor(nameToKeep).trim();
                        boolean hasInputFromName = !trimmedName.isEmpty() && !" ".equals(trimmedName) && !stripColor(getRedPromptName(p)).trim().equals(trimmedName);
                        Boolean prev = rightHasInputState.get(p.getUniqueId());
                        if (prev == null || !prev.equals(hasInputFromName)) {
                            // 绿色名称使用玩家当前输入文本，不走配置
                            String displayGreen = (input != null && !input.trim().isEmpty()) ? input : nameToKeep;
                            ItemStack out = createPane(
                                    hasInputFromName,
                                    hasInputFromName ? displayGreen : getRedPromptName(p),
                                    hasInputFromName ? getGreenLore(p) : getRedLore(p)
                            );
                            actions.add(setItemOutput.invoke(null, out));
                            rightHasInputState.put(p.getUniqueId(), hasInputFromName);
                        }
                    } catch (Throwable ignore) {}
                }

                        if (cdkText.isEmpty()) {
                            // 空输入：若点击输出槽则提示并关闭界面
                            if (slot == 2) {
                                Player p = (Player) stateSnapshotCls.getMethod("getPlayer").invoke(snapshot);
                                p.sendMessage(color("&c请输入CDK内容再兑换"));
                                try { activePlayers.remove(p.getUniqueId()); lastRenameText.remove(p.getUniqueId()); stopOverwriteTask(p); } catch (Throwable ignored) {}
                                try { actions.add(closeAction.invoke(null)); } catch (Throwable ignored) {}
                            }
                            // 同时保持左侧为纸张与空名称（若支持），供非输出槽点击时界面仍可更新
                            if (setLeftItem != null) {
                                try {
                                    Player p = (Player) stateSnapshotCls.getMethod("getPlayer").invoke(snapshot);
                                    actions.add(setLeftItem.invoke(null, createInputPaper(p)));
                                } catch (Throwable ignore) {}
                            }
                            return actions.isEmpty() ? java.util.Collections.emptyList() : actions;
                        } else {
                            // 非空输入：若点击输出槽则执行并关闭
                            if (slot == 2) {
                                Player p = (Player) stateSnapshotCls.getMethod("getPlayer").invoke(snapshot);
                                try { activePlayers.remove(p.getUniqueId()); lastRenameText.remove(p.getUniqueId()); stopOverwriteTask(p); } catch (Throwable ignored) {}
                                useCDK(p, cdkText);
                                actions.add(closeAction.invoke(null));
                            }
                            return actions.isEmpty() ? java.util.Collections.emptyList() : actions;
                        }
                    } catch (Throwable t) {
                        plugin.getLogger().warning("AnvilGUI onClick 处理失败: " + t.getMessage());
                        try { return java.util.Collections.singletonList(closeAction.invoke(null)); } catch (Throwable ignore) {}
                        return java.util.Collections.emptyList();
                    }
                };
                builderCls.getMethod("onClick", java.util.function.BiFunction.class).invoke(builder, onClick);

            // 初始设置右侧为红色提示按钮（名称为空格、红色 lore）（若 builder 支持）
            if (builderSupportsOutput) {
                try {
                    builderCls.getMethod("itemOutput", ItemStack.class).invoke(builder, createPane(false, getRedPromptName(player), getRedLore(player)));
                } catch (Throwable ignored) {}
            }
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
            activePlayers.add(player.getUniqueId());
            // 不预置 false，令定时任务在首次 tick 主动写入初始红色提示名
            rightHasInputState.remove(player.getUniqueId());
            startOverwriteTask(player);
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
            // 初始打开时设置右侧为红色提示按钮（名称与lore可配置），避免默认物品名
            try {
                inv.setItem(2, createPane(false, getRedPromptName(player), getRedLore(player)));
            } catch (Throwable ignored) {}
            fallbackPlayers.add(player.getUniqueId());
            rightHasInputState.put(player.getUniqueId(), false);
            startOverwriteTask(player);
        } catch (Throwable t) {
            player.sendMessage(color("&c无法打开铁砧输入界面，请使用指令：/cdk use <CDK>"));
        }
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        try {
            Player player = (Player) event.getView().getPlayer();
            if (!fallbackPlayers.contains(player.getUniqueId())) return;
            String rename = "";
            try {
                AnvilInventory anvil = (AnvilInventory) event.getInventory();
                String rt = anvil.getRenameText();
                rename = rt == null ? "" : rt;
            } catch (Throwable ignored) {
                // 兜底：尝试从左槽物品名读取（可能不可靠，但可提供回退）
                try {
                    ItemStack left = event.getInventory().getItem(0);
                    if (left != null) {
                        ItemMeta lm = left.getItemMeta();
                        if (lm != null && lm.hasDisplayName()) rename = lm.getDisplayName();
                    }
                } catch (Throwable ignored2) {}
            }
            String trimmed = stripColor(rename).trim();
            if (!rename.isEmpty()) {
                lastRenameText.put(player.getUniqueId(), stripColor(rename));
            } else {
                // 若没有自定义名称，则清空缓存
                lastRenameText.remove(player.getUniqueId());
            }
            // 根据输入动态设定右侧：无输入为红色提示，有输入为绿色确认（名称与lore均可配置）
            boolean hasInput = !trimmed.isEmpty() && !" ".equals(trimmed);
            event.setResult(createPane(
                    hasInput,
                    hasInput ? rename : getRedPromptName(player),
                    hasInput ? getGreenLore(player) : getRedLore(player)
            ));
        } catch (Throwable ignored) {}
    }

    @EventHandler
    public void onAnvilClick(InventoryClickEvent event) {
        try {
            if (event.getInventory() == null || event.getInventory().getType() != InventoryType.ANVIL) return;
            if (!(event.getWhoClicked() instanceof Player)) return;
            Player player = (Player) event.getWhoClicked();
            if (!fallbackPlayers.contains(player.getUniqueId())) return;
            if (event.getRawSlot() >= 0 && event.getRawSlot() < event.getView().getTopInventory().getSize() && event.getRawSlot() != 2) {
                event.setCancelled(true);
                return;
            }
            if (event.getRawSlot() >= event.getView().getTopInventory().getSize() && event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                event.setCancelled(true);
                return;
            }
            // 输出槽位索引为 2
            if (event.getRawSlot() == 2) {
                event.setCancelled(true);
                String input = "";
                try {
                    AnvilInventory anvil = (AnvilInventory) event.getInventory();
                    String rt = anvil.getRenameText();
                    input = rt == null ? "" : rt;
                } catch (Throwable ignored) {
                    input = lastRenameText.getOrDefault(player.getUniqueId(), "");
                }
                input = stripColor(input).trim();
                if (input.isEmpty()) {
                    // 提示需要先输入并关闭界面
                    try { event.setCurrentItem(createPane(false, getRedPromptName(player), getRedLore(player))); } catch (Throwable ignored) {}
                    player.sendMessage(color("&c请输入CDK内容再兑换"));
                    try { fallbackPlayers.remove(player.getUniqueId()); lastRenameText.remove(player.getUniqueId()); stopOverwriteTask(player); } catch (Throwable ignored) {}
                    player.closeInventory();
                    return;
                }
                useCDK(player, input);
                fallbackPlayers.remove(player.getUniqueId());
                lastRenameText.remove(player.getUniqueId());
                player.closeInventory();
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onManagedAnvilItemClick(InventoryClickEvent event) {
        try {
            if (event.getView() == null || event.getView().getTopInventory() == null) return;
            if (event.getView().getTopInventory().getType() != InventoryType.ANVIL) return;
            if (!(event.getWhoClicked() instanceof Player)) return;
            Player player = (Player) event.getWhoClicked();
            if (!isManagedAnvil(player)) return;

            int topSize = event.getView().getTopInventory().getSize();
            int rawSlot = event.getRawSlot();
            boolean clickedTopInventory = rawSlot >= 0 && rawSlot < topSize;

            // 1.21+ 中输入槽物品可能被取出；本插件 GUI 的非输出槽始终只是展示/输入载体。
            if (clickedTopInventory && rawSlot != 2) {
                event.setCancelled(true);
                return;
            }

            // 阻止玩家通过 Shift 点击背包物品灌入铁砧 GUI。
            if (!clickedTopInventory && event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                event.setCancelled(true);
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onManagedAnvilDrag(InventoryDragEvent event) {
        try {
            if (event.getView() == null || event.getView().getTopInventory() == null) return;
            if (event.getView().getTopInventory().getType() != InventoryType.ANVIL) return;
            if (!(event.getWhoClicked() instanceof Player)) return;
            Player player = (Player) event.getWhoClicked();
            if (!isManagedAnvil(player)) return;

            int topSize = event.getView().getTopInventory().getSize();
            for (Integer rawSlot : event.getRawSlots()) {
                if (rawSlot != null && rawSlot >= 0 && rawSlot < topSize) {
                    event.setCancelled(true);
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler
    public void onAnvilOpen(org.bukkit.event.inventory.InventoryOpenEvent event) {
        try {
            if (event.getInventory() == null || event.getInventory().getType() != InventoryType.ANVIL) return;
            if (!(event.getPlayer() instanceof Player)) return;
            Player player = (Player) event.getPlayer();
            if (!fallbackPlayers.contains(player.getUniqueId())) return;
            // 在打开后再次覆盖右侧为红色提示按钮（名称与lore可配置），避免默认物品名显示
            try {
                event.getInventory().setItem(2, createPane(false, getRedPromptName(player), getRedLore(player)));
            } catch (Throwable ignored) {}
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
            stopOverwriteTask(player);
        } catch (Throwable ignored) {}
    }

    // 使用 AnvilGUI 库后，不再需要拦截原版铁砧事件，避免冲突

    private boolean isManagedAnvil(Player player) {
        UUID uuid = player.getUniqueId();
        return activePlayers.contains(uuid) || fallbackPlayers.contains(uuid);
    }

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

    // 将列表逐项进行PAPI解析与颜色处理
    private List<String> parsePlaceholdersList(Player player, List<String> list) {
        if (list == null) return java.util.Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (String l : list) {
            out.add(parsePlaceholders(player, l));
        }
        return out;
    }

    // 读取红色提示名称（支持PAPI与颜色）
    private String getRedPromptName(Player player) {
        String name = config.getString("anvil_gui.output.red.name", RED_PROMPT_NAME);
        return color(parsePlaceholders(player, name));
    }

    // 读取绿色确认名称（支持PAPI与颜色）
    private String getGreenName(Player player) {
        String name = config.getString("anvil_gui.output.green.name", "&a点击确认");
        return color(parsePlaceholders(player, name));
    }

    // 读取红色提示lore（支持PAPI与颜色），默认空（不显示lore）
    private List<String> getRedLore(Player player) {
        List<String> lore = config.getStringList("anvil_gui.output.red.lore");
        if (lore == null) lore = java.util.Collections.emptyList();
        return color(parsePlaceholdersList(player, lore));
    }

    // 读取绿色确认lore（支持PAPI与颜色）
    private List<String> getGreenLore(Player player) {
        List<String> lore = config.getStringList("anvil_gui.output.green.lore");
        if (lore == null || lore.isEmpty()) lore = java.util.Arrays.asList("&a点击确认");
        return color(parsePlaceholdersList(player, lore));
    }

    // 解析材质字符串（支持新版名或旧版名:data），若解析失败则使用默认玻璃/纸
    private ItemStack parseMaterialToken(String token, boolean green) {
        try {
            if (token != null && !token.trim().isEmpty()) {
                String t = token.trim().toUpperCase(java.util.Locale.ROOT);
                String[] parts = t.split(":");
                Material m = Material.valueOf(parts[0]);
                if (parts.length >= 2) {
                    short data = Short.parseShort(parts[1]);
                    try { return new ItemStack(m, 1, data); } catch (Throwable ignored) { return new ItemStack(m, 1); }
                } else {
                    return new ItemStack(m, 1);
                }
            }
        } catch (Throwable ignored) {}
        // 回退默认玻璃
        try {
            Material modern = Material.valueOf(green ? "GREEN_STAINED_GLASS_PANE" : "RED_STAINED_GLASS_PANE");
            return new ItemStack(modern, 1);
        } catch (Throwable ignored) {}
        try {
            Material legacy = Material.valueOf("STAINED_GLASS_PANE");
            return new ItemStack(legacy, 1, (short) (green ? 13 : 14));
        } catch (Throwable ignored) {}
        return new ItemStack(Material.PAPER, 1);
    }

    // 根据配置生成右侧物品材质
    private ItemStack buildItemByConfig(boolean green) {
        String key = green ? "anvil_gui.output.green.material" : "anvil_gui.output.red.material";
        String token = config.getString(key, "");
        return parseMaterialToken(token, green);
    }

    // 动态覆写右侧确认物品，防止被铁砧或其他逻辑恢复默认名称
    private void startOverwriteTask(Player player) {
        try {
            stopOverwriteTask(player);
            overwriteTickCounts.put(player.getUniqueId(), 0);
            Runnable tick = () -> {
                try {
                    if (player == null || !player.isOnline()) return;
                    Inventory top = player.getOpenInventory().getTopInventory();
                    if (top == null || top.getType() != InventoryType.ANVIL) return;
                    int ticks = overwriteTickCounts.getOrDefault(player.getUniqueId(), 0);
                    overwriteTickCounts.put(player.getUniqueId(), ticks + 1);
                    // 读取右侧玻璃显示名：显示名存在且不为提示语则视为已输入；仅在状态切换时更新名称
                    String nameToKeep = "";
                    try {
                        ItemStack cur = top.getItem(2);
                        ItemMeta cm = cur != null ? cur.getItemMeta() : null;
                        if (cm != null && cm.hasDisplayName()) nameToKeep = cm.getDisplayName();
                    } catch (Throwable ignored) {}
                    String trimmedName = stripColor(nameToKeep).trim();
                    boolean hasInput = !trimmedName.isEmpty() && !" ".equals(trimmedName) && !stripColor(getRedPromptName(player)).trim().equals(trimmedName);
                    // 绿色名称使用玩家当前输入文本，不走配置
                    String displayGreen = null;
                    try {
                        if (top instanceof AnvilInventory) {
                            String rt = ((AnvilInventory) top).getRenameText();
                            if (rt != null && !rt.trim().isEmpty()) displayGreen = rt;
                        }
                    } catch (Throwable ignored) {}
                    if (displayGreen == null || displayGreen.trim().isEmpty()) displayGreen = nameToKeep;
                    String expectedName = hasInput ? displayGreen : getRedPromptName(player);
                    Boolean prev = rightHasInputState.get(player.getUniqueId());
                    boolean nameMismatch = !stripColor(nameToKeep).trim().equals(stripColor(expectedName).trim());
                    if (prev == null || !prev.equals(hasInput) || nameMismatch) {
                        ItemStack right = createPane(
                                hasInput,
                                expectedName,
                                hasInput ? getGreenLore(player) : getRedLore(player)
                        );
                        top.setItem(2, right);
                        rightHasInputState.put(player.getUniqueId(), hasInput);
                    }
                } catch (Throwable ignored) {}
            };
            Object handle = FoliaSupport.isFolia()
                    ? FoliaSupport.runEntityFixedRate(plugin, player, tick, 1L, 5L)
                    : FoliaSupport.runBukkitTimer(plugin, tick, 1L, 5L);
            overwriteTasks.put(player.getUniqueId(), handle);
        } catch (Throwable ignored) {}
    }

    private void stopOverwriteTask(Player player) {
        try {
            Object handle = overwriteTasks.remove(player.getUniqueId());
            FoliaSupport.cancel(handle);
            overwriteTickCounts.remove(player.getUniqueId());
            rightHasInputState.remove(player.getUniqueId());
        } catch (Throwable ignored) {}
    }

    // 不需要 getRenameTextSafe；交由 AnvilGUI 处理输入文本
    // 反射设置 CustomModelData，兼容旧版编译环境
    private void applyCustomModelData(ItemMeta meta, Integer cmd) {
        if (meta == null || cmd == null) return;
        try {
            java.lang.reflect.Method m = meta.getClass().getMethod("setCustomModelData", Integer.class);
            m.invoke(meta, cmd);
        } catch (Throwable ignored) {}
    }

    private Integer getCustomModelData(String key) {
        try {
            if (config.contains(key)) return config.getInt(key);
        } catch (Throwable ignored) {}
        return null;
    }

    private Integer getOutputCMD(boolean green) {
        return getCustomModelData(green ? "anvil_gui.output.green.custom_model_data" : "anvil_gui.output.red.custom_model_data");
    }
}
