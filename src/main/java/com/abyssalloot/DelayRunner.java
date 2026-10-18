package com.abyssalloot;

/**
 * 遅延実行キュー (絶対tickで発火). 隕石・爆縮・罠演出・突進の段打ちに使用する。
 * CombatHandler.onServerTick から {@link #tick()} を呼ぶと到達分を実行する。
 */
public final class DelayRunner {
    private DelayRunner() {}

    private record DelayTask(long targetTick, Runnable task) {}

    private static final java.util.List<DelayTask> PENDING =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    private static int current = 0;

    /** ticks 後に task を実行する (サーバtick基準) */
    public static void after(int ticks, Runnable task) {
        PENDING.add(new DelayTask(current + Math.max(0, ticks), task));
    }

    /** サーバtickごとに呼ぶ。到達した遅延タスクを発火・除去する */
    public static void tick() {
        current++;
        if (PENDING.isEmpty()) return;
        var done = new java.util.ArrayList<DelayTask>();
        synchronized (PENDING) {
            // 1パスで到達分を取り除く (removeAll は O(到達数×総数) になるため)
            var it = PENDING.iterator();
            while (it.hasNext()) {
                DelayTask d = it.next();
                if (d.targetTick() <= current) {
                    it.remove();
                    done.add(d);
                }
            }
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
