package dev.geri.playtime.database;

import dev.geri.playtime.PlayTime;
import dev.geri.playtime.utils.Time;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class Database {

    private final PlayTime plugin;
    private String connectionString;
    private Connection connection;

    public Database(PlayTime plugin) {
        this.plugin = plugin;
    }

    public void reload() {

        try {
            this.init();
            this.plugin.getLogger().info("SQLite connection starting...");
        } catch (Exception exception) {
            this.plugin.getLogger().severe("There was an error loading the database: " + exception.getMessage());
            Bukkit.getPluginManager().disablePlugin(this.plugin);
            return;
        }

        this.plugin.getLogger().info("Successfully loaded database!");
    }

    public void init() throws Exception {
        Class.forName("org.sqlite.JDBC");

        this.connectionString = "jdbc:sqlite:" + plugin.getDataFolder() + File.separator + plugin.getDescription().getName().toLowerCase() + ".db";
        this.connection = DriverManager.getConnection(connectionString);

        Statement createStatement = connection.createStatement();
        createStatement.executeUpdate("CREATE TABLE IF NOT EXISTS " + getTablePrefix() + "playtime (player_uuid VARCHAR(36) UNIQUE, minutes INT NOT NULL)");
    }

    public void connect(ConnectionCallback callback) {
        if (this.connection == null) {
            try {
                this.connection = DriverManager.getConnection(this.connectionString);
            } catch (SQLException exception) {
                this.plugin.getLogger().severe("An error occurred retrieving the SQLite database connection: " + exception.getMessage());
            }
        }

        try {
            callback.accept(this.connection);
        } catch (Exception exception) {
            this.plugin.getLogger().severe("An error occurred executing an SQLite query: " + exception.getMessage());
        }
    }


    public void getTop(int amount, Consumer<List<Time>> callback) {
        this.async(() -> this.connect((connection) -> {
            String query = "SELECT player_uuid, minutes FROM " + this.getTablePrefix() + "playtime ORDER BY minutes DESC LIMIT ?";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setInt(1, amount);

                List<Time> top = new ArrayList<>(amount);

                ResultSet resultSet = statement.executeQuery();
                while (resultSet.next()) {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(resultSet.getString("player_uuid")));
                    top.add(new Time(player, resultSet.getInt("minutes")));
                }

                callback.accept(top);
            }
        }));
    }

    public void getEverything(Consumer<List<Time>> callback) {
        this.async(() -> this.connect((connection) -> {
            try (Statement statement = connection.createStatement()) {
                List<Time> top = new ArrayList<>();

                ResultSet resultSet = statement.executeQuery("SELECT player_uuid, minutes FROM " + this.getTablePrefix() + "playtime ORDER BY minutes DESC");
                while (resultSet.next()) {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(resultSet.getString("player_uuid")));
                    top.add(new Time(player, resultSet.getInt("minutes")));
                }

                callback.accept(top);
            }
        }));
    }

    public void getSingle(UUID uuid, Consumer<Time> callback) {
        this.async(() -> this.connect((connection) -> {
            String query = "SELECT player_uuid, minutes FROM " + this.getTablePrefix() + "playtime WHERE player_uuid = ?";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, uuid.toString());

                ResultSet resultSet = statement.executeQuery();
                if (resultSet.next()) {
                    callback.accept(new Time(Bukkit.getOfflinePlayer(UUID.fromString(resultSet.getString("player_uuid"))), resultSet.getInt("minutes")));
                } else {
                    callback.accept(null);
                }
            }
        }));
    }

    public void updatePlaytime(UUID uuid, int minutes) {
        this.async(() -> this.connect((connection) -> {
            String query = "REPLACE INTO " + this.getTablePrefix() + "playtime (player_uuid, minutes) VALUES (?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, uuid.toString());
                statement.setInt(2, minutes);
                statement.executeUpdate();
            }
        }));
    }

    private void async(Runnable asyncCallback) {
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, asyncCallback);
    }

    public String getTablePrefix() {
        return this.plugin.getDescription().getName().toLowerCase() + '_';
    }

    // Automatically catch SQLExceptions
    interface ConnectionCallback {
        void accept(Connection connection) throws SQLException;
    }

}
