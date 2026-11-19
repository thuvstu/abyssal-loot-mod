package com.abyssalloot;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 案内文に出すキー名。サーバーはプレイヤーの割り当てを知らないので、{@link Component#keybind} で
 * クライアント側の実際の割り当て (変更後のキー) を表示させる。
 */
public final class KeyText {
    private KeyText() {}

    /** id = art / skill / dash / hub / next / sub / quick */
    public static MutableComponent key(String id) {
        return Component.literal("[").append(Component.keybind("key.abyssalloot." + id)).append("]");
    }

    /** "§f前置き" + [キー] + "§f後ろ" */
    public static MutableComponent line(String before, String id, String after) {
        return Component.literal(before).append(key(id)).append(Component.literal(after));
    }
}
