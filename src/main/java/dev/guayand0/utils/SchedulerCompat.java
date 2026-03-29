package dev.guayand0.utils;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.function.Consumer;

public class SchedulerCompat {

    private final JavaPlugin plugin;
    private final boolean folia;
    private final Object globalScheduler;
    private final Object asyncScheduler;

    public SchedulerCompat(JavaPlugin plugin) {
        this.plugin = plugin;

        boolean foliaDetected = false;
        Object global = null;
        Object async = null;

        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            Object server = Bukkit.getServer();
            Method getGlobalScheduler = server.getClass().getMethod("getGlobalRegionScheduler");
            Method getAsyncScheduler = server.getClass().getMethod("getAsyncScheduler");
            global = getGlobalScheduler.invoke(server);
            async = getAsyncScheduler.invoke(server);
            foliaDetected = true;
        } catch (Exception ignored) {}

        this.folia = foliaDetected;
        this.globalScheduler = global;
        this.asyncScheduler = async;
    }

    public boolean isFolia() {
        return folia;
    }

    public Object runGlobal(Runnable runnable) {
        if (!folia) {
            return Bukkit.getScheduler().runTask(plugin, runnable);
        }

        try {
            Method method = globalScheduler.getClass().getMethod("execute", Plugin.class, Runnable.class);
            method.invoke(globalScheduler, plugin, runnable);
            return null;
        } catch (Exception e) {
            throw new IllegalStateException("Folia global scheduler execute failed", e);
        }
    }

    public Object runGlobalLater(Runnable runnable, long delayTicks) {
        if (!folia) {
            return Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
        }

        if (delayTicks <= 0L) {
            return runGlobal(runnable);
        }

        try {
            Method method = globalScheduler.getClass().getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
            return method.invoke(globalScheduler, plugin, (Consumer<Object>) task -> runnable.run(), delayTicks);
        } catch (Exception e) {
            throw new IllegalStateException("Folia delayed scheduler failed", e);
        }
    }

    public Object runGlobalTimer(Runnable runnable, long delayTicks, long periodTicks) {
        if (!folia) {
            return Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks);
        }

        try {
            Method method = globalScheduler.getClass().getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
            return method.invoke(globalScheduler, plugin, (Consumer<Object>) task -> runnable.run(), Math.max(0L, delayTicks), Math.max(1L, periodTicks));
        } catch (Exception e) {
            throw new IllegalStateException("Folia fixed-rate scheduler failed", e);
        }
    }

    public Object runAsync(Runnable runnable) {
        if (!folia) {
            return Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
        }

        try {
            Method method = asyncScheduler.getClass().getMethod("runNow", Plugin.class, Consumer.class);
            return method.invoke(asyncScheduler, plugin, (Consumer<Object>) task -> runnable.run());
        } catch (Exception e) {
            throw new IllegalStateException("Folia async scheduler runNow failed", e);
        }
    }
}
