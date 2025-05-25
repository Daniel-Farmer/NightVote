package night.votes;

import night.votes.commands.NightVoteCommands;
import night.votes.listeners.PlayerChatListener;
import night.votes.listeners.PlayerConnectionListener;
import night.votes.managers.VoteManager;
import night.votes.tasks.NightCheckTask;
import night.votes.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class NightVote extends JavaPlugin {

    private static NightVote instance;
    private VoteManager voteManager;
    private NightCheckTask nightCheckTaskInstance;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig(); // Creates config.yml if it doesn't exist

        this.voteManager = new VoteManager(this);

        // Register listeners
        getServer().getPluginManager().registerEvents(new PlayerChatListener(this, this.voteManager), this);
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this, this.voteManager), this);

        // Register commands
        NightVoteCommands commandHandler = new NightVoteCommands(this, this.voteManager);
        getCommand("nightvote").setExecutor(commandHandler);
        getCommand("nightvote").setTabCompleter(commandHandler);


        // Schedule NightCheckTask
        this.nightCheckTaskInstance = new NightCheckTask(this, this.voteManager);
        this.nightCheckTaskInstance.runTaskTimer(this, 0L, 100L); // Checks every 5 seconds

        getLogger().info("NightVote has been enabled!");
    }

    @Override
    public void onDisable() {
        if (voteManager != null && voteManager.isVoteActive()) {
            // Use a specific message key for plugin disable
            String worldName = voteManager.getWorldForVote() != null ? voteManager.getWorldForVote().getName() : "an unknown world";
             Map<String, String> placeholders = new HashMap<>();
             placeholders.put("world", worldName);
            voteManager.cancelVote(getRawMessage("vote_cancelled_plugin_disabled", placeholders), false); // false for not resetting attempt cycle
        }
        if (nightCheckTaskInstance != null) {
            nightCheckTaskInstance.cancel();
        }
        getLogger().info("NightVote has been disabled!");
        instance = null;
    }

    public static NightVote getInstance() {
        return instance;
    }

    public VoteManager getVoteManager() {
        return voteManager;
    }

    public void reloadPlugin() {
        reloadConfig();
        // Potentially re-initialize parts that depend heavily on config, if necessary
        // For now, VoteManager and NightCheckTask read config on-the-fly or on startup.
        // NightCheckTask might need its world list updated if it caches it.
        // Let's ensure NightCheckTask re-reads enabled worlds.
        if (this.nightCheckTaskInstance != null) {
            this.nightCheckTaskInstance.cancel();
        }
        this.nightCheckTaskInstance = new NightCheckTask(this, this.voteManager);
        this.nightCheckTaskInstance.runTaskTimer(this, 0L, 100L);

        // Reset vote attempt cycle tracking in VoteManager
        voteManager.resetAllVoteAttemptedCycles();
        getLogger().info("NightVote configuration reloaded and tasks reset.");
    }

    // --- Configuration Getters ---

    public int getVoteDurationSeconds() {
        return getConfig().getInt("vote.duration_seconds", 30);
    }

    public long getNightStartTick() {
        return getConfig().getLong("vote.night_start_tick", 12550L);
    }

    public long getDayStartTick() {
        return getConfig().getLong("vote.day_start_tick", 1000L);
    }

    public int getMinPlayersToStartAutoVote() {
        return getConfig().getInt("vote.min_players_to_start_auto", 1);
    }
    
    public int getMinPlayersToStartManualVote() {
        return getConfig().getInt("vote.min_players_to_start_manual", 1);
    }

    public double getRequiredYesPercentage() {
        return getConfig().getDouble("vote.required_yes_percentage", 0.51);
    }

    public boolean attemptOncePerNightCycle() {
        return getConfig().getBoolean("vote.attempt_once_per_night_cycle_per_world", true);
    }

    public List<String> getEnabledWorldsForAutoVote() {
        return getConfig().getStringList("enabled_worlds_for_auto_vote");
    }

    public List<World> getOnlineEnabledWorlds() {
        return getEnabledWorldsForAutoVote().stream()
                .map(Bukkit::getWorld)
                .filter(world -> world != null && world.getEnvironment() == World.Environment.NORMAL)
                .collect(Collectors.toList());
    }

    public String getMessage(String key, Map<String, String> placeholders) {
        String prefix = getConfig().getString("messages.prefix", "&6[NightVote] ");
        String message = getConfig().getString("messages." + key);

        if (message == null) {
            Bukkit.getLogger().warning("[NightVote] Missing message for key: messages." + key);
            return ChatColor.RED + "Missing message: messages." + key;
        }

        message = prefix + message;

        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("%" + entry.getKey() + "%", entry.getValue() != null ? entry.getValue() : "null");
            }
        }
        return Utils.colorize(message);
    }
    
    public String getRawMessage(String key, Map<String, String> placeholders) {
        String message = getConfig().getString("messages." + key);

        if (message == null) {
            Bukkit.getLogger().warning("[NightVote] Missing message for key: messages." + key);
            return ChatColor.RED + "Missing message: messages." + key;
        }
        
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("%" + entry.getKey() + "%", entry.getValue() != null ? entry.getValue() : "null");
            }
        }
        return Utils.colorize(message);
    }
}