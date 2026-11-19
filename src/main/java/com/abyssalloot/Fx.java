package com.abyssalloot;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * 粒子で描く図形 (輪・螺旋・柱・放射) と、奥義の発動演出。
 * サーバー側で sendParticles するので、周りのプレイヤーにも同じ演出が見える。
 */
public final class Fx {
    private Fx() {}

    /** 水平の輪 (半径 r・n 点) */
    public static void ring(ServerLevel level, Vec3 c, double r, int n, ParticleOptions p) {
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            level.sendParticles(p, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    /** 上へ巻き上がる螺旋 (高さ h・巻き数 turns) */
    public static void spiral(ServerLevel level, Vec3 c, double r, double h, double turns, int n, ParticleOptions p) {
        for (int i = 0; i < n; i++) {
            double t = (double) i / n;
            double a = t * turns * Math.PI * 2;
            level.sendParticles(p, c.x + Math.cos(a) * r, c.y + t * h, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    /** 2 点を結ぶ線 */
    public static void line(ServerLevel level, Vec3 a, Vec3 b, int n, ParticleOptions p) {
        for (int i = 0; i <= n; i++) {
            Vec3 q = a.lerp(b, (double) i / n);
            level.sendParticles(p, q.x, q.y, q.z, 1, 0, 0, 0, 0);
        }
    }

    /** 武器色の粒子 (大きさ s) */
    public static ParticleOptions dust(int color, float s) {
        return new DustParticleOptions(color, s);
    }

    /**
     * 奥義の発動演出: 足元に武器色の衝撃波の輪が 3 段で広がり、体の周りに螺旋が立ち上り、頭上に光の柱。
     * 神話級の武器 (または鍛造 +5) なら金の輪と閃光を足す。
     */
    public static void artBurst(ServerPlayer player, int color, boolean grand) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 feet = player.position().add(0, 0.1, 0);
        var col = dust(color, 1.4f);
        spiral(level, feet, 1.1, 2.6, 2.0, 40, col);
        line(level, feet.add(0, 2.2, 0), feet.add(0, 5.0, 0), 14, ParticleTypes.END_ROD);
        for (int k = 0; k < 3; k++) {
            final int step = k;
            DelayRunner.after(2 + k * 3, () -> {
                if (!player.isAlive()) return;
                Vec3 at = player.position().add(0, 0.15, 0);
                ring(level, at, 1.6 + step * 1.4, 18 + step * 8, dust(color, 1.6f - step * 0.3f));
                if (grand) ring(level, at.add(0, 0.05, 0), 1.2 + step * 1.6, 14 + step * 6, dust(0xFFD23C, 1.0f));
            });
        }
        if (grand) {
            level.sendParticles(ParticleTypes.FIREWORK, feet.x, feet.y + 1.2, feet.z, 40, 0.2, 0.2, 0.2, 0.35);
            level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.5f, 1.4f);
        }
    }
}
