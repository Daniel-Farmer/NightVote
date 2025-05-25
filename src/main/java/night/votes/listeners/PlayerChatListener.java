package night.votes.listeners;

import night.votes.NightVote;
import night.votes.managers.VoteManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

public class PlayerChatListener implements Listener {

    private final NightVote plugin;
    private final VoteManager voteManager;

    public PlayerChatListener(NightVote plugin, VoteManager voteManager) {
        this.plugin = plugin;
        this.voteManager = voteManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true) // High to catch before other plugins potentially
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!voteManager.isVoteActive()) {
            return;
        }

        Player player = event.getPlayer();

        // Check permission to vote
        if (!player.hasPermission("nightvote.vote")) {
            return; // Silently ignore if no permission, or send a message
        }
        
        // Check if player is in the world where vote is active
        if (voteManager.getWorldForVote() != null && !player.getWorld().equals(voteManager.getWorldForVote())) {
            // Player is not in the voting world. Optionally inform them if they try to vote.
            // For now, we just don't process their Y/N as a vote if they aren't in the world.
            return;
        }


        String message = event.getMessage().trim();

        if (message.equalsIgnoreCase("Y") || message.equalsIgnoreCase("yes") ||
            message.equalsIgnoreCase("N") || message.equalsIgnoreCase("no")) {
            
            // Check again if player is in the correct world, in case they teleported
            // between the initial check and now (though unlikely for AsyncPlayerChatEvent).
            // This is more crucial if voteManager.getWorldForVote() could change rapidly.
            if (voteManager.getWorldForVote() == null || !player.getWorld().equals(voteManager.getWorldForVote())) {
                 Map<String, String> placeholders = new HashMap<>();
                 placeholders.put("world", voteManager.getWorldForVote() != null ? voteManager.getWorldForVote().getName() : "the vote world");
                 player.sendMessage(plugin.getMessage("not_eligible_world", placeholders));
                 event.setCancelled(true); // Cancel their "Y/N" message
                 return;
            }

            final String voteChoice = message;
            new BukkitRunnable() {
                @Override
                public void run() {
                    // Final check for active vote and world, as BukkitRunnable runs later
                    if (voteManager.isVoteActive() && voteManager.getWorldForVote() != null && 
                        player.isOnline() && player.getWorld().equals(voteManager.getWorldForVote())) {
                        voteManager.addVote(player, voteChoice);
                    } else if (voteManager.isVoteActive()) {
                        // Vote is active but player is no longer eligible (e.g. changed world)
                         Map<String, String> placeholders = new HashMap<>();
                         placeholders.put("world", voteManager.getWorldForVote() != null ? voteManager.getWorldForVote().getName() : "the vote world");
                         player.sendMessage(plugin.getMessage("not_eligible_world", placeholders));
                    } else {
                        // Vote ended before this runnable executed
                        player.sendMessage(plugin.getMessage("no_active_vote", null));
                    }
                }
            }.runTask(plugin); // Run on the main server thread
            
            event.setCancelled(true); // Cancel the Y/N message from appearing in global chat
        }
    }
}