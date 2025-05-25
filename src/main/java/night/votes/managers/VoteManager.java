package night.votes.managers;

import night.votes.NightVote;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class VoteManager {

    private final NightVote plugin;
    private boolean voteActive = false;
    private final Map<UUID, Boolean> playerVotes = new HashMap<>(); // true for YES, false for NO
    private Set<UUID> eligibleVoters = new HashSet<>();
    private BukkitTask voteEndTask;
    private World worldForVote; // The world where the current vote is active

    // Tracks if a vote has been attempted in a specific world during the current night cycle
    private final Map<String, Boolean> worldVoteAttemptedThisCycle = new HashMap<>();

    public VoteManager(NightVote plugin) {
        this.plugin = plugin;
    }

    public boolean isVoteActive() {
        return voteActive;
    }

    public World getWorldForVote() {
        return worldForVote;
    }

    public boolean hasVoted(Player player) {
        return playerVotes.containsKey(player.getUniqueId());
    }

    public boolean getVoteAttemptedThisCycleForWorld(String worldName) {
        return worldVoteAttemptedThisCycle.getOrDefault(worldName, false);
    }

    public void setVoteAttemptedThisCycleForWorld(String worldName, boolean attempted) {
        if (attempted && plugin.attemptOncePerNightCycle()) {
            worldVoteAttemptedThisCycle.put(worldName, true);
        } else if (!attempted) {
            worldVoteAttemptedThisCycle.remove(worldName);
        }
    }
    
    public void resetVoteAttemptedThisCycleForWorld(String worldName) {
        worldVoteAttemptedThisCycle.remove(worldName);
        plugin.getLogger().info("Vote attempt cycle reset for world: " + worldName);
    }

    public void resetAllVoteAttemptedCycles() {
        worldVoteAttemptedThisCycle.clear();
        plugin.getLogger().info("All world vote attempt cycles have been reset.");
    }

    public boolean startVote(World world, boolean isManual, Player initiator) {
        if (voteActive) {
            // Inform initiator if manual and vote active elsewhere
            if (isManual && initiator != null) {
                 Map<String, String> placeholders = new HashMap<>();
                 placeholders.put("other_world", worldForVote != null ? worldForVote.getName() : "another world");
                initiator.sendMessage(plugin.getMessage("vote_already_active_elsewhere", placeholders));
            }
            return false; // Global vote already active
        }
        if (world == null || world.getEnvironment() != World.Environment.NORMAL) {
            if (isManual && initiator != null) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("world", world != null ? world.getName() : "unknown");
                initiator.sendMessage(plugin.getMessage("error_world_not_overworld", placeholders));
            }
            return false;
        }

        int minPlayers = isManual ? plugin.getMinPlayersToStartManualVote() : plugin.getMinPlayersToStartAutoVote();
        long playersInWorld = world.getPlayers().size();

        if (playersInWorld < minPlayers) {
            if (isManual && initiator != null) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("world", world.getName());
                placeholders.put("min_players", String.valueOf(minPlayers));
                placeholders.put("online_players", String.valueOf(playersInWorld));
                initiator.sendMessage(plugin.getMessage("not_enough_players_manual", placeholders));
            } else if (!isManual) { // Log for auto vote if below threshold
                 Map<String, String> placeholders = new HashMap<>();
                 placeholders.put("world", world.getName());
                 placeholders.put("min_players", String.valueOf(minPlayers));
                 placeholders.put("online_players", String.valueOf(playersInWorld));
                 // Bukkit.broadcastMessage(plugin.getRawMessage("min_players_not_met_for_auto", placeholders)); // maybe too spammy
                 plugin.getLogger().info(plugin.getRawMessage("min_players_not_met_for_auto", placeholders));
            }
            setVoteAttemptedThisCycleForWorld(world.getName(), false); // Allow another attempt if players join
            return false;
        }

        this.worldForVote = world;
        this.voteActive = true;
        this.playerVotes.clear();
        this.eligibleVoters = world.getPlayers().stream()
                                .map(Player::getUniqueId)
                                .collect(Collectors.toSet()); // Players currently in the target world

        if (plugin.attemptOncePerNightCycle()) {
            setVoteAttemptedThisCycleForWorld(world.getName(), true);
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("world", world.getName());
        placeholders.put("time", String.valueOf(plugin.getVoteDurationSeconds()));
        
        String startMessageKey = isManual ? "vote_start_manual" : "vote_started";
        if (isManual && initiator != null) placeholders.put("player", initiator.getName());

        // Broadcast to players in the specific world
        for (Player p : world.getPlayers()) {
            p.sendMessage(plugin.getMessage(startMessageKey, placeholders));
            p.sendMessage(plugin.getMessage("vote_instructions", placeholders));
        }
        plugin.getLogger().info("Night vote started in " + world.getName() + (isManual ? " by " + (initiator != null ? initiator.getName() : "CONSOLE") : " automatically") + ". Eligible: " + eligibleVoters.size());


        voteEndTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (voteActive && worldForVote.equals(world)) { // Ensure it's still the same vote
                    endVote();
                }
            }
        }.runTaskLater(plugin, plugin.getVoteDurationSeconds() * 20L);
        return true;
    }

    public void addVote(Player player, String voteChoice) {
        if (!voteActive || worldForVote == null) {
            player.sendMessage(plugin.getMessage("no_active_vote", null));
            return;
        }
        if (!player.getWorld().equals(worldForVote)) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("world", worldForVote.getName());
            player.sendMessage(plugin.getMessage("not_eligible_world", placeholders));
            return;
        }
        if (!eligibleVoters.contains(player.getUniqueId())) {
            player.sendMessage(plugin.getMessage("not_eligible_general", null));
            return;
        }
        if (hasVoted(player)) {
            player.sendMessage(plugin.getMessage("already_voted", null));
            return;
        }

        boolean decision;
        if (voteChoice.equalsIgnoreCase("Y") || voteChoice.equalsIgnoreCase("yes")) {
            decision = true;
        } else if (voteChoice.equalsIgnoreCase("N") || voteChoice.equalsIgnoreCase("no")) {
            decision = false;
        } else {
            return;
        }

        playerVotes.put(player.getUniqueId(), decision);
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());
        placeholders.put("vote", decision ? "&aYES" : "&cNO");

        // Broadcast to players in the voting world
        for (Player p : worldForVote.getPlayers()) {
            p.sendMessage(plugin.getMessage("player_voted", placeholders));
        }


        // Check if all currently eligible players in the world have voted
        long currentEligibleOnlineCount = eligibleVoters.stream()
                                                .map(Bukkit::getPlayer)
                                                .filter(p -> p != null && p.isOnline() && p.getWorld().equals(worldForVote))
                                                .count();
        if (playerVotes.size() >= currentEligibleOnlineCount && currentEligibleOnlineCount > 0) {
            endVote();
        }
    }

    public void handlePlayerQuit(Player player) {
        if (!voteActive || worldForVote == null || !player.getWorld().equals(worldForVote)) {
            return; // Player was not in the vote world or no vote active
        }

        UUID playerId = player.getUniqueId();
        boolean wasEligible = eligibleVoters.remove(playerId); // Remove from eligible if they were

        if (playerVotes.containsKey(playerId)) {
            playerVotes.remove(playerId);
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", player.getName());
            placeholders.put("world", worldForVote.getName());
            // Broadcast to remaining players in the voting world
            for (Player p : worldForVote.getPlayers()) {
                 p.sendMessage(plugin.getMessage("player_left_vote_removed", placeholders));
            }
        }

        // If they were eligible and left, re-evaluate if vote should end
        if (wasEligible) {
            // Count remaining eligible online players
            Set<UUID> stillEligibleAndOnline = eligibleVoters.stream()
                .filter(id -> Bukkit.getPlayer(id) != null && Bukkit.getPlayer(id).isOnline() && Bukkit.getPlayer(id).getWorld().equals(worldForVote))
                .collect(Collectors.toSet());

            if (stillEligibleAndOnline.isEmpty() && voteActive) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("world", worldForVote.getName());
                // Broadcast to players in the voting world (if any left, or globally if preferred)
                for (Player p : Bukkit.getOnlinePlayers()) { // Or just worldForVote.getPlayers()
                    p.sendMessage(plugin.getMessage("all_eligible_left", placeholders));
                }
                cancelVote(plugin.getRawMessage("all_eligible_left", placeholders), true);
                return;
            }
            
            // If all remaining eligible players have now voted
            if (voteActive && !stillEligibleAndOnline.isEmpty() && playerVotes.keySet().containsAll(stillEligibleAndOnline)) {
                 endVote();
            }
        }
    }
    
    public void handlePlayerChangedWorld(Player player, World fromWorld) {
        if (!voteActive || worldForVote == null || !fromWorld.equals(worldForVote)) {
            return; // Player was not in the vote world or no vote active
        }
        // Treat as if they quit the vote world for eligibility and vote counting
        handlePlayerQuit(player); 
    }


    private void endVote() {
        if (!voteActive || worldForVote == null) return;

        clearVoteEndTask();

        // Recalculate eligible voters who are still online in the correct world AT THE END
        Set<UUID> finalEligibleVoters = eligibleVoters.stream()
            .map(Bukkit::getPlayer)
            .filter(p -> p != null && p.isOnline() && p.getWorld().equals(worldForVote))
            .map(Player::getUniqueId)
            .collect(Collectors.toSet());
        
        int yesVotes = 0;
        int noVotes = 0;
        // Count votes only from those who are still eligible and online
        for (Map.Entry<UUID, Boolean> entry : playerVotes.entrySet()) {
            if (finalEligibleVoters.contains(entry.getKey())) {
                if (entry.getValue()) yesVotes++;
                else noVotes++;
            }
        }

        int currentEligibleCount = finalEligibleVoters.size();

        Map<String, String> resultPlaceholders = new HashMap<>();
        resultPlaceholders.put("yes_votes", String.valueOf(yesVotes));
        resultPlaceholders.put("no_votes", String.valueOf(noVotes));
        resultPlaceholders.put("eligible_voters", String.valueOf(currentEligibleCount));
        resultPlaceholders.put("world", worldForVote.getName());

        String resultMessage = plugin.getMessage("vote_finished", resultPlaceholders);
        for (Player p : worldForVote.getPlayers()) {
            p.sendMessage(resultMessage);
        }


        boolean passed = false;
        if (currentEligibleCount > 0) {
            double yesPercentage = (double) yesVotes / currentEligibleCount;
            if (yesVotes > 0 && yesPercentage >= plugin.getRequiredYesPercentage()) { // Use >= for percentage
                passed = true;
            }
        }
        // Edge case: if required_yes_percentage is 0 and there's 1 yes vote with 0 eligible (e.g. all left), it could pass.
        // The current logic (currentEligibleCount > 0) handles this well.

        if (passed) {
            for (Player p : worldForVote.getPlayers()) {
                p.sendMessage(plugin.getMessage("vote_passed", resultPlaceholders));
            }
            if (worldForVote.getEnvironment() == World.Environment.NORMAL) {
                worldForVote.setTime(plugin.getDayStartTick());
            } else {
                 for (Player p : worldForVote.getPlayers()) {
                    p.sendMessage(plugin.getMessage("error_changing_time", resultPlaceholders));
                }
            }
        } else {
             for (Player p : worldForVote.getPlayers()) {
                p.sendMessage(plugin.getMessage("vote_failed", resultPlaceholders));
            }
        }

        resetVoteStateAfterOutcome(true); // true to mark attempt for this world's cycle
    }

    public void cancelVote(String reason, boolean maintainAttemptCycle) {
        if (!voteActive || worldForVote == null) return;

        clearVoteEndTask();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("reason", reason);
        placeholders.put("world", worldForVote.getName());
        
        String messageKey = "vote_cancelled_reason";
        // Check if the reason matches specific raw message keys for more tailored output
        if (reason.equals(plugin.getRawMessage("all_eligible_left", placeholders))) {
            // Message already handled by handlePlayerQuit or similar
        } else if (reason.equals(plugin.getRawMessage("vote_cancelled_plugin_disabled", placeholders))) {
             messageKey = "vote_cancelled_plugin_disabled"; // Use the specific message without prefix from NightVote
        }


        String cancelMessage = (messageKey.equals("vote_cancelled_plugin_disabled"))
            ? plugin.getRawMessage(messageKey, placeholders) // Use raw if it's a special internal message
            : plugin.getMessage(messageKey, placeholders);    // Use prefixed for general reasons


        for (Player p : worldForVote.getPlayers()) {
            p.sendMessage(cancelMessage);
        }
        
        resetVoteStateAfterOutcome(maintainAttemptCycle);
        if (!maintainAttemptCycle) {
            // If explicitly told not to maintain the cycle (e.g. admin cancel), reset it for this world
            resetVoteAttemptedThisCycleForWorld(worldForVote.getName());
        }
    }

    private void clearVoteEndTask() {
        if (voteEndTask != null) {
            try {
                voteEndTask.cancel();
            } catch (IllegalStateException e) {
                // Task may have already run or been cancelled
            }
            voteEndTask = null;
        }
    }

    private void resetVoteStateAfterOutcome(boolean markAttemptForCycle) {
        String previousVoteWorldName = (this.worldForVote != null) ? this.worldForVote.getName() : null;
        
        this.voteActive = false;
        this.playerVotes.clear();
        this.eligibleVoters.clear();
        this.worldForVote = null; // Crucial: set worldForVote to null

        if (previousVoteWorldName != null) {
            if (markAttemptForCycle && plugin.attemptOncePerNightCycle()) {
                // This is usually set at the start of the vote.
                // Ensure it's set if an outcome (pass/fail/timeout) occurred.
                setVoteAttemptedThisCycleForWorld(previousVoteWorldName, true);
            }
            // else if !markAttemptForCycle, the caller (e.g. cancel command) might reset it.
        }
    }
}