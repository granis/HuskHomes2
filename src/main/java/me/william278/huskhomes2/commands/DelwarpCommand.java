package me.william278.huskhomes2.commands;

import me.william278.huskhomes2.MessageManager;
import me.william278.huskhomes2.teleport.SettingHandler;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;

public class DelwarpCommand extends CommandBase {

    @Override
    protected boolean onCommand(Player p, Command command, String label, String[] args) {
        if (args.length == 1) {
            String warpName = args[0];
            SettingHandler.deleteWarp(p, warpName);
        } else {
            MessageManager.sendMessage(p, "error_invalid_syntax", command.getUsage());
        }
        return true;
    }

}