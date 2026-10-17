package com.abyssalloot;

/**
 * 遁E��実行キュー (絶対tickで発火). 隕石・爁E��・罠演�E・突E��の段打ちに使用する、E * CombatHandler.onServerTick から {@link #tick()} を呼ぶと到達�Eを実行する、E */
public final class DelayRunner {
    private DelayRunner() {}

    private record DelayTask(long targetTick, Runnable task) {}

    private static final java.util.List<DelayTask> PENDING =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    private static int current = 0;

    /** ticks 後に task を実行すめE(サーバtick基溁E */
    public static void after(int ticks, Runnable task) {
        PENDING.add(new DelayTask(current + Math.max(0, ticks), task));
    }

    /** サーバtickごとに呼ぶ。到達した遅延タスクを発火・除去する */
    public static void tick() {
        current++;
        if (PENDING.isEmpty()) return;
        var done = new java.util.ArrayList<DelayTask>();
        synchronized (PENDING) {
            for (DelayTask d : PENDING) {
                if (d.targetTick() <= current) done.add(d);
            }
            PENDING.removeAll(done);
        }
        for (DelayTask d : done) {
            try {
                d.task().run();
            } catch (Exception ex) {
                AbyssalLootMod.LOGGER.warn("delayed task failed", ex);
            }
        }
    }
}
