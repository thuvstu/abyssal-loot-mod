
Installation information
=======

This template repository can be directly cloned to get you started with a new
mod. Simply create a new repository cloned from this one, by following the
instructions provided by [GitHub](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-repository-from-a-template).

Once you have your clone, simply open the repository in the IDE of your choice. The usual recommendation for an IDE is either IntelliJ IDEA or Eclipse.

If at any point you are missing libraries in your IDE, or you've run into problems you can
run `gradlew --refresh-dependencies` to refresh the local cache. `gradlew clean` to reset everything 
{this does not affect your code} and then start the process again.

Mapping Names:
============
By default, the MDK is configured to use the official mapping names from Mojang for methods and fields 
in the Minecraft codebase. These names are covered by a specific license. All modders should be aware of this
license. For the latest license text, refer to the mapping file itself, or the reference copy here:
https://github.com/NeoForged/NeoForm/blob/main/Mojang.md

Additional Resources: 
==========
Community Documentation: https://docs.neoforged.net/  
NeoForged Discord: https://discord.neoforged.net/

## Abyssal Loot: はじめかた (日本語)

1. ゲームに入ると案内が出ます。**[はじめる]** をクリックすると、剣聖＋剣を配備してすぐ出撃します。
2. 別の職業や武器で始めたい場合は `/abyss quick <職業> <武器>` を入力します。
   - 職業: `samurai` `mage` `ninja` `paladin` `gunner`
   - 武器: `sword` `axe` `spear` `staff` `bow` `dagger` `hammer` `katana` `scythe` `gauntlet` `chakram` `tome`
   - 進行中の冒険は破棄されます。
3. 操作説明は `/abyss help` です（クリックできるボタン付き）。
   - 移動 WASD / 攻撃 左クリック / **E** 武器奥義 / **Q** 必殺技 / **C** 回避
4. 開発者向け: `./gradlew runClient`（または `runServer`）で起動します。
