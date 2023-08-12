package me.EtienneDx.RealEstate;

import me.EtienneDx.RealEstate.Transactions.Transaction;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;

public class RentCommandExecutor implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;
        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(player.getLocation(), false, null);

        if (claim == null) {
            player.sendMessage("You are not standing inside a claim.");
            return true;
        }

        Transaction tr = RealEstate.transactionsStore.getTransaction(claim);
        if (tr == null) {
            player.sendMessage("This claim is not available for rent.");
            return true;
        }

        // Process the rent transaction
        tr.interact(player);
        return true;
    }
}
