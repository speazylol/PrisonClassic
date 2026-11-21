package dev.prisonclassic.util;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;

public final class Players {
    private Players() {
    }

    public static Player attacker(Entity var0) {
        if (var0 instanceof Player var4) {
            return var4;
        } else {
            return var0 instanceof Projectile var1 && var1.getShooter() instanceof Player var2 ? var2 : null;
        }
    }
}
