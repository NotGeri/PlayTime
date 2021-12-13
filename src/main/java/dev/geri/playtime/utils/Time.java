package dev.geri.playtime.utils;

import org.bukkit.OfflinePlayer;

public record Time(OfflinePlayer player, int minutes) implements Comparable<Time> {

    public OfflinePlayer getPlayer() {
        return this.player;
    }

    public int getMinutes() {
        return this.minutes;
    }

    @Override
    public int compareTo(Time o) {
        return Integer.compare(o.minutes, this.minutes);
    }

}