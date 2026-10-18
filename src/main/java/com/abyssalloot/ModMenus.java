package com.abyssalloot;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * GUI のコンテナ登録 (鍛造 / 商店 / 装備比較。ステータスはテキスト同期のSyncパケットで表示)。
 */
public final class ModMenus {
    private ModMenus() {}

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, AbyssalLootMod.MODID);

    public static final Supplier<MenuType<ForgeMenu>> FORGE =
            MENUS.register("forge", () -> IMenuTypeExtension.create(ForgeMenu::new));

    public static final Supplier<MenuType<ShopMenu>> SHOP =
            MENUS.register("shop", () -> IMenuTypeExtension.create(ShopMenu::new));

    public static final Supplier<MenuType<CompareMenu>> COMPARE =
            MENUS.register("compare", () -> IMenuTypeExtension.create(CompareMenu::new));
}
