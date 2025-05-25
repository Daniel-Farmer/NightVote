package night.votes.tasks;

import night.votes.NightVote;
import night.votes.managers.VoteManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NightCheckTask extends BukkitRunnable {

    private final NightVote plugin;
    private final VoteManager voteManager;

    public NightCheckTask(NightVote plugin, VoteManager voteManager) {
        this.plugin = plugin;
        this.voteManager = voteManager;
    }

    @Override
    public void run() {
        if (voteManager.isVoteActive()) {
            return; // A vote is already in progress globally, don't start another.
        }

        List<World> enabledWorlds = plugin.getOnlineEnabledWorlds();
        if (enabledWorlds.isEmpty()) {
            return; // No configured (or loaded) worlds for auto-voting.
        }

        long nightStartTick = plugin.getNightStartTick();
        // Define a reasonable "night approaching" window end, e.g., 1000 ticks after nightStartTick
        long nightApproachingWindowEnd = nightStartTick + 1000L; // e.g., if night starts at 12550, window is 12550-13550

        for (World world : enabledWorlds) {
            if (world.getEnvironment() != World.Environment.NORMAL) continue; // Should be filtered by getOnlineEnabledWorlds, but double-check

            long currentTime = world.getTime();
            String worldName = world.getName();

            boolean isNightApproaching = currentTime >= nightStartTick && currentTime < nightApproachingWindowEnd;
            boolean isDayTime = currentTime < nightStartTick || currentTime > 23000L; // Before night or late night/early morning

            if (isNightApproaching) {
                if (plugin.attemptOncePerNightCycle() && voteManager.getVoteAttemptedThisCycleForWorld(worldName)) {
                    // Vote already attempted for this world in this cycle, skip.
                    continue;
                }

                // Check player count for this specific world
                int playersInWorld = world.getPlayers().size();
                int minPlayersForAuto = plugin.getMinPlayersToStartAutoVote();

                if (playersInWorld >= minPlayersForAuto) {
                    // Attempt to start vote. If successful, it sets voteActive and returns true.
                    // We only want one vote active at a time across all worlds.
                    if (voteManager.startVote(world, false, null)) {
                        plugin.getLogger().info("Automatic night vote triggered for world: " + worldName);
                        return; // Exit after successfully starting one vote.
                    } else {
                        // Could not start vote (e.g. another vote started *just* now by another thread/check - unlikely but possible)
                        // Or min player check inside startVote failed (should be caught above, but defensive)
                        // Or world was not overworld (should be caught above)
                    }
                } else {
                    // Not enough players in this world for an auto vote.
                    // Reset attempt cycle if players dropped below threshold to allow a new vote if they return
                    // Only reset if a vote wasn't already attempted this cycle.
                    // If voteManager.getVoteAttemptedThisCycleForWorld(worldName) is true, we don't reset it here
                    // as it implies a vote did happen, and it should stay attempted until full day.
                    if (!voteManager.getVoteAttemptedThisCycleForWorld(worldName)) {
                        voteManager.resetVoteAttemptedThisCycleForWorld(worldName); // Ensure it's clear if no attempt was made
                    }
                    // Optional: Log this to console if desired
                    // plugin.getLogger().info("Skipping auto-vote in " + worldName + ": " + playersInWorld + "/" + minPlayersForAuto + " players.");
                }
            } else if (isDayTime) {
                // If it's day time, reset the "vote attempted" flag for this world for the *next* night.
                if (voteManager.getVoteAttemptedThisCycleForWorld(worldName)) {
                    voteManager.resetVoteAttemptedThisCycleForWorld(worldName);
                     // plugin.getLogger().info("Daytime detected in " + worldName + ". Resetting night vote attempt status.");
                }
            }
        }
    }
}