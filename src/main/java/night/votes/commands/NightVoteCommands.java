package night.votes.commands;

import night.votes.NightVote;
import night.votes.managers.VoteManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class NightVoteCommands implements CommandExecutor, TabCompleter {

    private final NightVote plugin;
    private final VoteManager voteManager;

    public NightVoteCommands(NightVote plugin, VoteManager voteManager) {
        this.plugin = plugin;
        this.voteManager = voteManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(plugin.getMessage("command_usage_main", null));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "start":
                handleStartCommand(sender, args);
                break;
            case "cancel":
                handleCancelCommand(sender);
                break;
            case "reload":
                handleReloadCommand(sender);
                break;
            default:
                sender.sendMessage(plugin.getMessage("command_usage_main", null));
                break;
        }
        return true;
    }

    private void handleStartCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nightvote.start")) {
            sender.sendMessage(plugin.getMessage("no_permission", null));
            return;
        }

        if (voteManager.isVoteActive()) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("other_world", voteManager.getWorldForVote() != null ? voteManager.getWorldForVote().getName() : "another world");
            sender.sendMessage(plugin.getMessage("vote_already_active_elsewhere", placeholders));
            return;
        }

        World targetWorld = null;
        if (args.length > 1) {
            targetWorld = Bukkit.getWorld(args[1]);
            if (targetWorld == null) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("world", args[1]);
                sender.sendMessage(plugin.getMessage("error_world_not_found", placeholders));
                return;
            }
        } else if (sender instanceof Player) {
            targetWorld = ((Player) sender).getWorld();
        } else {
            // If console and no world specified, pick first available overworld or error
            List<World> overworlds = Bukkit.getWorlds().stream()
                                        .filter(w -> w.getEnvironment() == World.Environment.NORMAL)
                                        .collect(Collectors.toList());
            if (!overworlds.isEmpty()) {
                targetWorld = overworlds.get(0);
                sender.sendMessage(plugin.getRawMessage("prefix", null) + "&7Defaulting to world: " + targetWorld.getName());
            } else {
                 sender.sendMessage(plugin.getMessage("command_usage_start", null));
                 sender.sendMessage(plugin.getRawMessage("prefix", null) + "&cNo Overworld found to start a vote in, or specify a world name.");
                 return;
            }
        }

        if (targetWorld.getEnvironment() != World.Environment.NORMAL) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("world", targetWorld.getName());
            sender.sendMessage(plugin.getMessage("error_world_not_overworld", placeholders));
            return;
        }
        
        Player initiator = (sender instanceof Player) ? (Player) sender : null;
        if (!voteManager.startVote(targetWorld, true, initiator)) {
            // startVote will send specific error messages if it fails (e.g. not enough players)
            // No need to send a generic "failed to start" here unless startVote doesn't cover a case.
        } else {
            // Message is handled by startVote if successful.
            // sender.sendMessage(plugin.getMessage("vote_started", placeholders)); // Already done by startVote
        }
    }

    private void handleCancelCommand(CommandSender sender) {
        if (!sender.hasPermission("nightvote.cancel")) {
            sender.sendMessage(plugin.getMessage("no_permission", null));
            return;
        }

        if (!voteManager.isVoteActive()) {
            sender.sendMessage(plugin.getMessage("no_active_vote", null));
            return;
        }

        String reason = (sender instanceof Player) ? "Manually cancelled by " + sender.getName() : "Manually cancelled by CONSOLE";
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("world", voteManager.getWorldForVote().getName());
        placeholders.put("player", sender.getName());

        voteManager.cancelVote(plugin.getRawMessage("vote_cancel_manual", placeholders), false); // false to reset attempt cycle
        // The cancelVote method now handles broadcasting the cancellation message.
        // sender.sendMessage(plugin.getRawMessage("prefix", null) + "&eVote has been cancelled."); // Generic confirmation to sender
    }

    private void handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("nightvote.reload")) {
            sender.sendMessage(plugin.getMessage("no_permission", null));
            return;
        }
        plugin.reloadPlugin();
        sender.sendMessage(plugin.getMessage("reload_success", null));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        List<String> subCommands = Arrays.asList("start", "cancel", "reload");

        if (args.length == 1) {
            for (String subCmd : subCommands) {
                if (subCmd.toLowerCase().startsWith(args[0].toLowerCase())) {
                    // Check permissions for tab completion
                    if (subCmd.equals("start") && sender.hasPermission("nightvote.start")) completions.add(subCmd);
                    else if (subCmd.equals("cancel") && sender.hasPermission("nightvote.cancel")) completions.add(subCmd);
                    else if (subCmd.equals("reload") && sender.hasPermission("nightvote.reload")) completions.add(subCmd);
                    else if (!subCmd.equals("start") && !subCmd.equals("cancel") && !subCmd.equals("reload")) completions.add(subCmd); // For future expansion
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("start") && sender.hasPermission("nightvote.start")) {
            String currentArg = args[1].toLowerCase();
            Bukkit.getWorlds().stream()
                  .filter(world -> world.getEnvironment() == World.Environment.NORMAL)
                  .map(World::getName)
                  .filter(name -> name.toLowerCase().startsWith(currentArg))
                  .forEach(completions::add);
        }
        return completions;
    }
}