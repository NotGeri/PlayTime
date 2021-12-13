package dev.geri.playtime;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public class CustomPlaceholder extends PlaceholderExpansion {

    private final PlayTime plugin;

    public CustomPlaceholder(PlayTime plugin) {
        this.plugin = plugin;
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null) return "";

        return switch (identifier.toLowerCase()) {
            case "time" -> this.plugin.getPlaytimeDisplay(this.plugin.getMinutes(player));
            case "position" -> String.valueOf(this.plugin.getPosition(player.getUniqueId()));
            default -> null;
        };

    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String getIdentifier() {
        return "playtime";
    }

    @Override
    public String getAuthor() {
        return this.plugin.getDescription().getAuthors().toString();
    }

    @Override
    public String getVersion() {
        return this.plugin.getDescription().getVersion();
    }

}
