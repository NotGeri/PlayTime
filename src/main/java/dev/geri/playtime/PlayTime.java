package dev.geri.playtime;

import dev.geri.playtime.database.Database;
import dev.geri.playtime.utils.HexUtils;
import dev.geri.playtime.utils.StringPlaceholders;
import dev.geri.playtime.utils.Time;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.StringUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PlayTime extends JavaPlugin implements Listener {

    private Database database;
    private Map<UUID, Integer> positionCache;

    @Override
    public void onEnable() {
        this.positionCache = new ConcurrentHashMap<>();

        // Register commands
        this.getCommand("playtime").setExecutor(this);
        this.getCommand("playtop").setExecutor(this);
        this.getCommand("playtimereload").setExecutor(this);

        // Register placeholder
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) new CustomPlaceholder(this).register();

        // Load configs and database
        this.reload();

        // Register events
        Bukkit.getPluginManager().registerEvents(this, this);

        // Start async update scheduler
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::updatePositionCache, 0L, 100L);
    }

    public void reload() {
        this.saveDefaultConfig();
        this.reloadConfig();

        if (this.database == null) this.database = new Database(this);

        this.database.reload();
        this.positionCache.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        switch (command.getName().toLowerCase()) {

            case "playtime" -> {
                if (!sender.hasPermission("playtime.use")) {
                    this.sendMessage(sender, "permission");
                    return true;
                }

                Consumer<Time> run = playTime -> {
                    if (playTime == null) {
                        this.sendMessage(sender, "player");
                        return;
                    }

                    if (!positionCache.containsKey(playTime.getPlayer().getUniqueId())) {
                        this.sendMessage(sender, "loading");
                        return;
                    }

                    int position = this.positionCache.get(playTime.getPlayer().getUniqueId());

                    StringPlaceholders placeholders = StringPlaceholders.builder("player", playTime.getPlayer().getName())
                            .addPlaceholder("playtime_time", this.getPlaytimeDisplay(playTime.getMinutes()))
                            .addPlaceholder("playtime_position", position)
                            .build();

                    this.sendMessage(sender, "playtime", placeholders);
                };


                if (args.length > 0 && sender.hasPermission("playtime.use.other")) {
                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
                    if (target.isOnline()) {
                        run.accept(new Time(target, this.getMinutes(target)));
                    } else {
                        this.database.getSingle(target.getUniqueId(), run);
                    }
                    return true;
                }

                if (!(sender instanceof Player player)) {
                    this.sendMessage(sender, "console");
                    return true;
                }

                run.accept(new Time(player, this.getMinutes(player)));
            }

            case "playtop" -> {
                if (!sender.hasPermission("playtime.top")) {
                    this.sendMessage(sender, "permission");
                    return true;
                }

                int amount = this.getConfig().getInt("playtime-top");
                if (sender.hasPermission("playtime.top.unlimited") && args.length > 0) {
                    try {
                        amount = Integer.parseInt(args[0]);
                    } catch (NumberFormatException exception) {
                        this.sendMessage(sender, "invalid");
                        return true;
                    }
                }

                if (amount < 1) amount = 1;

                this.getTop(amount, playTimes -> {
                    this.sendMessage(sender, "playtop.head");

                    for (int i = 1; i <= playTimes.size(); i++) {
                        Time playTime = playTimes.get(i - 1);

                        StringPlaceholders placeholders = StringPlaceholders.builder("player", playTime.getPlayer().getName())
                                .addPlaceholder("playtime_position", i)
                                .addPlaceholder("playtime_time", this.getPlaytimeDisplay(playTime.getMinutes()))
                                .build();

                        this.sendMessage(sender, "playtop.lines", placeholders);
                    }

                    this.sendMessage(sender, "playtop.foot");
                });
            }

            case "playtimereload" -> {
                if (!sender.hasPermission("playtime.reload")) {
                    this.sendMessage(sender, "permission");
                    return true;
                }

                this.reload();
                this.sendMessage(sender, "reload");
            }

        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("playtime") && sender.hasPermission("playtime.use.other")) {

            if (args.length == 0) return Bukkit.getOnlinePlayers().stream().filter(x -> !this.isVanished(x)).map(Player::getName).collect(Collectors.toList());

            List<String> possibilities = Bukkit.getOnlinePlayers().stream().filter(x -> !this.isVanished(x)).map(Player::getName).collect(Collectors.toList());
            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], possibilities, completions);

            return completions;

        }

        return Collections.emptyList();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.database.updatePlaytime(event.getPlayer().getUniqueId(), this.getMinutes(event.getPlayer()));
    }

    private void updatePositionCache() {

        this.database.getEverything(data -> {

            List<Time> onlinePlaytime = Bukkit.getOnlinePlayers().stream().map(x -> new Time(x, this.getMinutes(x))).collect(Collectors.toList());

            List<Time> combined = new ArrayList<>(onlinePlaytime.size() + data.size());

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!this.isVanished(player)) data.removeIf(x -> x.getPlayer() == player);
            }

            combined.addAll(data);
            combined.addAll(onlinePlaytime);
            combined = combined.stream().sorted().collect(Collectors.toList());

            this.positionCache.clear();

            for (int i = 1; i <= combined.size(); i++) {
                this.positionCache.put(combined.get(i - 1).getPlayer().getUniqueId(), i);
            }

        });
    }

    public void getTop(int amount, Consumer<List<Time>> callback) {
        this.database.getTop(amount, data -> {
            List<Time> onlinePlaytime = Bukkit.getOnlinePlayers().stream().map(x -> new Time(x, this.getMinutes(x))).collect(Collectors.toList());

            List<Time> combined = new ArrayList<>(onlinePlaytime.size() + data.size());
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!this.isVanished(player)) {
                    data.removeIf(x -> x.getPlayer() == player);
                } else {
                    onlinePlaytime.removeIf(x -> x.getPlayer() == player);
                }
            }

            combined.addAll(data);
            combined.addAll(onlinePlaytime);
            combined = combined.stream().sorted().limit(amount).collect(Collectors.toList());

            callback.accept(combined);
        });
    }

    public int getMinutes(OfflinePlayer player) {
        return player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20 / 60;
    }

    public boolean isVanished(OfflinePlayer player) {
        return player.isOnline() && ((Player) player).getMetadata("vanished").stream().anyMatch(MetadataValue::asBoolean);
    }

    public void sendMessage(CommandSender sender, String messageKey) {
        this.sendMessage(sender, messageKey, StringPlaceholders.empty());
    }

    public void sendMessage(CommandSender sender, String messageKey, StringPlaceholders placeholders) {
        String message = HexUtils.colorify(this.replacePlaceholders(sender, placeholders.apply(this.getConfig().getString("messages." + messageKey))));

        String[] split = message.split(Pattern.quote("\\n"), -1);
        for (String chunk : split)
            sender.sendMessage(chunk);
    }

    public String getPlaytimeDisplay(int totalMinutes) {
        int days = totalMinutes / 24 / 60;
        int hours = totalMinutes / 60 % 24;
        int minutes = totalMinutes % 60;

        return this.getMessage("playtime-format", StringPlaceholders.builder("days", days).addPlaceholder("hours", hours).addPlaceholder("minutes", minutes).build());
    }

    private String replacePlaceholders(CommandSender sender, String message) {
        return sender instanceof Player && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI") ? PlaceholderAPI.setPlaceholders((Player) sender, message) : message;
    }

    // Get leaderboard position
    public int getPosition(UUID uuid) {
        return this.positionCache.get(uuid);
    }

    public String getMessage(String messageKey, StringPlaceholders placeholders) {
        return placeholders.apply(this.getConfig().getString("messages." + messageKey));
    }

}
