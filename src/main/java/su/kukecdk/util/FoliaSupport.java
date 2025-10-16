package su.kukecdk.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * 通过反射适配 Folia 与 Spigot/Paper 的调度差异。
 * - 不引入 Folia API 编译期依赖，避免旧版编译环境报错
 * - 在 Folia 上优先使用 GlobalRegion/Entity 调度；在非 Folia 上使用 BukkitScheduler
 */
public final class FoliaSupport {
    private FoliaSupport() {}

    /** 判断当前是否为 Folia 服务器 */
    public static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 在非 Folia 上运行 Bukkit 定时任务（返回 BukkitTask 句柄） */
    public static Object runBukkitTimer(JavaPlugin plugin, Runnable runnable, long initialDelayTicks, long periodTicks) {
        return Bukkit.getScheduler().runTaskTimer(plugin, runnable, initialDelayTicks, periodTicks);
    }

    /**
     * 在 Folia 的全局区域调度器上周期运行（返回 Folia ScheduledTask 句柄），否则退回 BukkitTimer。
     */
    public static Object runGlobalFixedRate(JavaPlugin plugin, Runnable runnable, long initialDelayTicks, long periodTicks) {
        if (!isFolia()) {
            return runBukkitTimer(plugin, runnable, initialDelayTicks, periodTicks);
        }
        try {
            Method getGRS = Bukkit.class.getMethod("getGlobalRegionScheduler");
            Object grs = getGRS.invoke(null);
            Method runAtFixedRate = null;
            for (Method m : grs.getClass().getMethods()) {
                if (m.getName().equals("runAtFixedRate")) {
                    Class<?>[] p = m.getParameterTypes();
                    if (p.length == 4 && JavaPlugin.class.isAssignableFrom(p[0]) && p[1].getName().contains("Consumer") && p[2] == long.class && p[3] == long.class) {
                        runAtFixedRate = m;
                        break;
                    }
                }
            }
            if (runAtFixedRate == null) {
                return runBukkitTimer(plugin, runnable, initialDelayTicks, periodTicks);
            }
            Class<?> consumerClass = runAtFixedRate.getParameterTypes()[1];
            Object consumer = Proxy.newProxyInstance(
                    consumerClass.getClassLoader(),
                    new Class<?>[]{consumerClass},
                    (proxy, method, args) -> {
                        if ("accept".equals(method.getName())) {
                            runnable.run();
                        }
                        return null;
                    }
            );
            return runAtFixedRate.invoke(grs, plugin, consumer, initialDelayTicks, periodTicks);
        } catch (Throwable t) {
            return runBukkitTimer(plugin, runnable, initialDelayTicks, periodTicks);
        }
    }

    /**
     * 在 Folia 的玩家实体调度器上周期运行（返回 Folia ScheduledTask 句柄），否则退回 BukkitTimer。
     */
    public static Object runEntityFixedRate(JavaPlugin plugin, Player player, Runnable runnable, long initialDelayTicks, long periodTicks) {
        if (!isFolia()) {
            return runBukkitTimer(plugin, runnable, initialDelayTicks, periodTicks);
        }
        try {
            Method getScheduler = player.getClass().getMethod("getScheduler");
            Object entityScheduler = getScheduler.invoke(player);
            Method runAtFixedRate = null;
            for (Method m : entityScheduler.getClass().getMethods()) {
                if (m.getName().equals("runAtFixedRate")) {
                    Class<?>[] p = m.getParameterTypes();
                    if (p.length == 4 && JavaPlugin.class.isAssignableFrom(p[0]) && p[1].getName().contains("Consumer") && p[2] == long.class && p[3] == long.class) {
                        runAtFixedRate = m;
                        break;
                    }
                }
            }
            if (runAtFixedRate == null) {
                return runBukkitTimer(plugin, runnable, initialDelayTicks, periodTicks);
            }
            Class<?> consumerClass = runAtFixedRate.getParameterTypes()[1];
            Object consumer = Proxy.newProxyInstance(
                    consumerClass.getClassLoader(),
                    new Class<?>[]{consumerClass},
                    (proxy, method, args) -> {
                        if ("accept".equals(method.getName())) {
                            runnable.run();
                        }
                        return null;
                    }
            );
            return runAtFixedRate.invoke(entityScheduler, plugin, consumer, initialDelayTicks, periodTicks);
        } catch (Throwable t) {
            return runBukkitTimer(plugin, runnable, initialDelayTicks, periodTicks);
        }
    }

    /** 取消任务句柄，兼容 BukkitTask 与 Folia ScheduledTask */
    public static void cancel(Object taskHandle) {
        if (taskHandle == null) return;
        try {
            if (taskHandle instanceof BukkitTask) {
                ((BukkitTask) taskHandle).cancel();
                return;
            }
            Method cancel = taskHandle.getClass().getMethod("cancel");
            cancel.invoke(taskHandle);
        } catch (Throwable ignored) {}
    }
}