package subaraki.telepads.server;

import net.minecraft.server.level.ServerPlayer;

public record ExperienceCost(int levels, int points) {
    public ExperienceCost { if (levels < 0 || points < 0) throw new IllegalArgumentException("Negative experience cost"); }
    public static long pointsAtLevel(int level) {
        long n = level;
        if (level <= 16) return n * n + 6 * n;
        if (level <= 31) return (5 * n * n - 81 * n + 720) / 2;
        return (9 * n * n - 325 * n + 4440) / 2;
    }
    public static int nextLevel(int level) { return level >= 30 ? 112 + (level - 30) * 9 : level >= 15 ? 37 + (level - 15) * 5 : 7 + level * 2; }
    public static long balance(int level, float progress) { return pointsAtLevel(level) + Math.round(progress * nextLevel(level)); }
    public boolean affordable(int level, float progress) { return levels > 0 ? level >= levels : balance(level, progress) >= points; }
    public boolean affordable(ServerPlayer player) { return affordable(player.experienceLevel, player.experienceProgress); }
    public void charge(ServerPlayer player) {
        if (levels > 0) player.giveExperienceLevels(-levels);
        else if (points > 0) player.giveExperiencePoints(-points);
    }
}
