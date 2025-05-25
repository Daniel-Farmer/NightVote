package night.votes.listeners;

import night.votes.NightVote;
import night.votes.managers.VoteManager;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

public class PlayerConnectionListener implements Listener {

    private final NightVote plugin;
    private final VoteManager voteManager;

    public PlayerConnectionListener(NightVote plugin, VoteManager voteManager) {
        this.plugin = plugin;
        this.voteManager = voteManager;
    }

    @EventHandler(priority = EventPriority.MONITOR) // Monitor so other plugins can handle join first
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        World playerWorld = player.getWorld();

        if (voteManager.isVoteActive() && playerWorld.equals(voteManager.getWorldForVote())) {
            // Run with a delay to allow other plugins to process join / player to fully load
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline() && player.getWorld().equals(voteManager.getWorldForVote())) { // Re-check state
                        Map<String, String> placeholders = new HashMap<>();
                        placeholders.put("world", playerWorld.getName());
                        
                        player.sendMessage(plugin.getMessage("vote_in_progress_join_world", placeholders));
                        player.sendMessage(plugin.getMessage("vote_in_progress_join_instructions", placeholders));
                        // Note: Players joining mid-vote are NOT automatically added to eligibleVoters in current VoteManager logic.
                        // This is by design for simplicity: vote starts with current world population.
                        // If they were *already* eligible (e.g. re-logged quickly), they can still vote.
                    }
                }
            }.runTaskLater(plugin, 40L); // 2 seconds delay
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (voteManager.isVoteActive() && voteManager.getWorldForVote() != null && 
            player.getWorld().equals(voteManager.getWorldForVote())) {
            // Run on next tick to avoid issues with player object state during quit
            // and ensure voteManager logic is sound with player having left the world (for vote count).
            new BukkitRunnable() {
                @Override
                public void run() {
                    // Player object from event is still valid for UUID/name, but getPlayer() might be null
                    voteManager.handlePlayerQuit(event.getPlayer());
                }
            }.runTask(plugin);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World fromWorld = event.getFrom(); // The world the player left

        if (voteManager.isVoteActive() && voteManager.getWorldForVote() != null) {
            // If player left the world where vote is active
            if (fromWorld.equals(voteManager.getWorldForVote())) {
                 new BukkitRunnable() {
                    @Override
                    public void run() {
                        voteManager.handlePlayerChangedWorld(player, fromWorld);
                    }
                }.runTask(plugin);
            }
            // If player entered the world where vote is active
            else if (player.getWorld().equals(voteManager.getWorldForVote())) {
                 new BukkitRunnable() {
                    @Override
                    public void run() {
                         if (player.isOnline() && player.getWorld().equals(voteManager.getWorldForVote())) { // Re-check state
                            Map<String, String> placeholders = new HashMap<>();
                            placeholders.put("world", player.getWorld().getName());
                            player.sendMessage(plugin.getMessage("vote_in_progress_join_world", placeholders));
                            player.sendMessage(plugin.getMessage("vote_in_progress_join_instructions", placeholders));
                        }
                    }
                }.runTaskLater(plugin, 20L); // 1 second delay
            }
        }
    }
}