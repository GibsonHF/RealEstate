package me.EtienneDx.RealEstate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import me.EtienneDx.RealEstate.Transactions.*;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.earth2me.essentials.User;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.HelpCommand;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

@CommandAlias("re|realestate")
public class RECommand extends BaseCommand
{
	@Subcommand("info")
	@Description("Gives the player informations about the claim he is standing in")
	@CommandPermission("realestate.info")
	public static void info(Player player)
	{
		if(RealEstate.transactionsStore.anyTransaction(
				GriefPrevention.instance.dataStore.getClaimAt(((Player)player).getLocation(), false, null)))
		{
			Transaction tr = RealEstate.transactionsStore.getTransaction(
					GriefPrevention.instance.dataStore.getClaimAt(((Player)player).getLocation(), false, null));
			tr.preview((Player)player);
		}
		else
		{
			Messages.sendMessage(player, RealEstate.instance.messages.msgNoTransactionFoundHere);
		}

	}

	@Subcommand("list")
	@Description("Displays the list of all real estate offers currently existing in a GUI")
	@CommandCompletion("all|sell|rent|lease")
	@Syntax("[all|sell|rent|lease] <page>")
	public static void list(CommandSender sender, @Optional String type, @Default("1") int page)
	{
		if (!(sender instanceof Player))
		{
			Messages.sendMessage(sender, "This command can only be used by a player.");
			return;
		}

		Player player = (Player) sender;

		// Get the relevant transactions based on the type
		ArrayList<Transaction> transactions = getRelevantTransactions(type);

		if(transactions.isEmpty())
		{
			Messages.sendMessage(player, RealEstate.instance.messages.msgNoTransactionFound);
			return;
		}

		// Create and populate the GUI
		Inventory inv = Bukkit.createInventory(null, 54, "Real Estate Listings");
		populateGUI(transactions, inv);

		// Open the GUI for the player
		player.openInventory(inv);
	}

	private static ArrayList<Transaction> getRelevantTransactions(String type)
	{
		ArrayList<Transaction> transactions = new ArrayList<Transaction>();

		if(type == null || type.equalsIgnoreCase("all"))
		{
			transactions.addAll(RealEstate.transactionsStore.claimSell.values());
			transactions.addAll(RealEstate.transactionsStore.claimRent.values());
			transactions.addAll(RealEstate.transactionsStore.claimLease.values());
		}
		else if(type.equalsIgnoreCase("sell"))
		{
			transactions.addAll(RealEstate.transactionsStore.claimSell.values());
		}
		else if(type.equalsIgnoreCase("rent"))
		{
			transactions.addAll(RealEstate.transactionsStore.claimRent.values());
		}
		else if(type.equalsIgnoreCase("lease"))
		{
			transactions.addAll(RealEstate.transactionsStore.claimLease.values());
		}

		return transactions;
	}

	private static void populateGUI(ArrayList<Transaction> transactions, Inventory inv)
	{

		for (Transaction tr : transactions)
		{
			if (tr instanceof ClaimRent) {
				ClaimRent rentTransaction = (ClaimRent) tr;
				if (rentTransaction.buyer != null) {
					continue;  // Property is currently rented, skip
				}
			}
			// Create the item
			ItemStack item = new ItemStack(Material.OAK_SIGN);
			ItemMeta meta = item.getItemMeta();

			if (tr.getHolder() != null)
				continue; // Skip if the claim is no longer valid
			// Set the display name as the claim's ID (You need to implement getClaimId() based on your data structure)
			Claim claimForTransaction = GriefPrevention.instance.dataStore.getClaimAt(tr.getHolder().getLocation(), false, null);
			meta.setDisplayName(ChatColor.DARK_AQUA + "Claim ID: "+ ChatColor.WHITE + claimForTransaction.getID()); // If getID() exists

			// Get price and owner from the transaction
			String transactionType;
			double price = getPriceFromTransaction(tr);
			String owner = getOwnerFromTransaction(tr);
			if (tr instanceof ClaimRent) {
				transactionType = "Rent";
				price = ((ClaimRent) tr).price;
			} else if (tr instanceof ClaimSell) {
				transactionType = "Sell";
				price = ((ClaimSell) tr).price;
			} else if (tr instanceof ClaimLease) {
				transactionType = "Lease";
				price = ((ClaimLease) tr).price;
			} else {
				transactionType = "Unknown"; // Default case, should ideally never hit this
			}

			// Set the lore with owner, price, and interaction instructions
			List<String> lore = Arrays.asList(
					ChatColor.GOLD+"Type: " + transactionType,
					ChatColor.WHITE + "Owner: " + owner,
					ChatColor.YELLOW+"Price: " + price,
					ChatColor.GOLD + "Left-Click to teleport",
					ChatColor.AQUA + "Claim Size: "+ ChatColor.WHITE + claimForTransaction.getArea() +" blocks"
			);

			meta.setLore(lore);
			item.setItemMeta(meta);

			inv.addItem(item);
		}
	}

	public static Transaction getTransactionByName(String claimName) {
		List<Transaction> allTransactions = new ArrayList<>();
		allTransactions.addAll(RealEstate.transactionsStore.claimSell.values());
		allTransactions.addAll(RealEstate.transactionsStore.claimRent.values());
		allTransactions.addAll(RealEstate.transactionsStore.claimLease.values());

		for (Transaction tr : allTransactions) {
			if (getOwnerFromTransaction(tr).equals(claimName)) {
				return tr;
			}
		}
		return null;
	}


	// Helper function to get the price based on transaction type
	private static double getPriceFromTransaction(Transaction tr)
	{
		// You need to determine how you extract or calculate the price from the transaction object
		if (tr instanceof ClaimRent)
		{
			return ((ClaimRent) tr).price;
		}
		else if (tr instanceof ClaimSell)
		{
			return ((ClaimSell) tr).price;
		}
		else if (tr instanceof ClaimLease)
		{
			return ((ClaimLease) tr).price;
		}
		return 0; // Default value if no match
	}

	// Helper function to get the owner of the claim
	private static String getOwnerFromTransaction(Transaction tr)
	{
		// This is just a placeholder, you need to adjust based on your data structure
		if (tr.getOwner() == null)
		{
			return "Admin";  // Placeholder for an admin claim
		}
		return Bukkit.getOfflinePlayer(tr.getOwner()).getName();
	}



	@Subcommand("renewrent")
	@Description("Allows the player renting a claim or subclaim to enable or disable the automatic renew of his rent")
	@Conditions("partOfRent")
    @CommandCompletion("enable|disable")
	@Syntax("[enable|disable]")
	public static void renewRent(Player player, @Optional String newStatus)
	{
		Location loc = player.getLocation();
		Claim claim = GriefPrevention.instance.dataStore.getClaimAt(loc, false, null);
		ClaimRent cr = (ClaimRent)RealEstate.transactionsStore.getTransaction(claim);
		String claimType = claim.parent == null ? 
			RealEstate.instance.messages.keywordClaim : RealEstate.instance.messages.keywordSubclaim;
		if(!RealEstate.instance.config.cfgEnableAutoRenew)
		{
			Messages.sendMessage(player, RealEstate.instance.messages.msgErrorAutoRenewDisabled);
			return;
		}
		if(newStatus == null)
		{
			Messages.sendMessage(player, RealEstate.instance.messages.msgRenewRentCurrently, cr.autoRenew ? 
					RealEstate.instance.messages.keywordEnabled :
					RealEstate.instance.messages.keywordDisabled,
				claimType);
		}
		else if(!newStatus.equalsIgnoreCase("enable") && !newStatus.equalsIgnoreCase("disable"))
		{
			Messages.sendMessage(player, RealEstate.instance.messages.msgErrorCommandUsage, "/re renewrent [enable|disable]");
		}
		else if(cr.buyer != null && cr.buyer.equals(player.getUniqueId()))
		{
			cr.autoRenew = newStatus.equalsIgnoreCase("enable");
			RealEstate.transactionsStore.saveData();
			Messages.sendMessage(player, RealEstate.instance.messages.msgRenewRentNow, cr.autoRenew ? 
					RealEstate.instance.messages.keywordEnabled :
					RealEstate.instance.messages.keywordDisabled,
				claimType);
		}
		else
		{
			Messages.sendMessage(player, RealEstate.instance.messages.msgErrorBuyerOnly);
		}
	}
	
	@Subcommand("exitoffer")
	@Conditions("partOfBoughtTransaction")
	public class ExitOfferCommand extends BaseCommand
	{
		@Subcommand("info")
		@Default
		@Description("View informations about the exit offer")
		public void info(Player player)
		{
			BoughtTransaction bt = (BoughtTransaction)RealEstate.transactionsStore.getTransaction(player);
			if(bt.exitOffer == null)
			{
				Messages.sendMessage(player, RealEstate.instance.messages.msgInfoExitOfferNone);
			}
			else if(bt.exitOffer.offerBy.equals(player.getUniqueId()))
			{
				Messages.sendMessage(player, RealEstate.instance.messages.msgInfoExitOfferMadeByStatus, 
						RealEstate.econ.format(bt.exitOffer.price));
				Messages.sendMessage(player, RealEstate.instance.messages.msgInfoExitOfferCancel, 
						"/re exitoffer cancel");
			}
			else// it is the other player
			{
				Messages.sendMessage(player, RealEstate.instance.messages.msgInfoExitOfferMadeToStatus, 
					Bukkit.getOfflinePlayer(bt.exitOffer.offerBy).getName(), RealEstate.econ.format(bt.exitOffer.price));
				Messages.sendMessage(player, RealEstate.instance.messages.msgInfoExitOfferAccept, 
						"/re exitoffer accept");
				Messages.sendMessage(player, RealEstate.instance.messages.msgInfoExitOfferReject,
						"/re exitoffer refuse");
			}
		}
		
		@Subcommand("create")
		@Description("Creates an offer to break an ongoing transaction")
		@Syntax("<price>")
		public void create(Player player, @Conditions("positiveDouble") Double price)
		{
			BoughtTransaction bt = (BoughtTransaction)RealEstate.transactionsStore.getTransaction(player);
			if(bt.exitOffer != null)
			{
				Messages.sendMessage(player, RealEstate.instance.messages.msgErrorExitOfferAlreadyExists);
				return;
			}
			if(bt.buyer == null)
			{
				Messages.sendMessage(player, RealEstate.instance.messages.msgErrorExitOfferNoBuyer);
				return;
			}
			bt.exitOffer = new ExitOffer(player.getUniqueId(), price);

			Messages.sendMessage(player, RealEstate.instance.messages.msgInfoExitOfferCreatedBySelf, 
					RealEstate.econ.format(price));

			UUID other = player.getUniqueId().equals(bt.owner) ? bt.buyer : bt.owner;
			if(other != null)// not an admin claim
			{
				OfflinePlayer otherP = Bukkit.getOfflinePlayer(other);
				Location loc = player.getLocation();
				String claimType = GriefPrevention.instance.dataStore.getClaimAt(loc, false, null).parent == null ? 
					RealEstate.instance.messages.keywordClaim : RealEstate.instance.messages.keywordSubclaim;
				String location = "[" + loc.getWorld().getName() + ", X: " + loc.getBlockX() + ", Y: " + loc.getBlockY() + ", Z: "
					+ loc.getBlockZ() + "]";

				if(otherP.isOnline())
				{
					Messages.sendMessage(otherP.getPlayer(), RealEstate.instance.messages.msgInfoExitOfferCreatedByOther, 
							player.getName(), claimType, RealEstate.econ.format(price), location);
				}
				else if(RealEstate.instance.config.cfgMailOffline && RealEstate.ess != null)
	        	{
	        		User u = RealEstate.ess.getUser(other);
	        		u.addMail(Messages.getMessage(RealEstate.instance.messages.msgInfoExitOfferCreatedByOther, 
							player.getName(), claimType, RealEstate.econ.format(price), location));
	        	}
			}
		}
		
		@Subcommand("accept")
		@Description("Accepts an offer to break an ongoing transaction")
		public void accept(Player player)
		{
			Location loc = player.getLocation();
			Claim claim = GriefPrevention.instance.dataStore.getClaimAt(loc, false, null);
			BoughtTransaction bt = (BoughtTransaction)RealEstate.transactionsStore.getTransaction(claim);
			String claimType = claim.parent == null ? "claim" : "subclaim";
			if(bt.exitOffer == null)
			{
				Messages.sendMessage(player, RealEstate.instance.messages.msgErrorExitOfferNone);
			}
			else if(bt.exitOffer.offerBy.equals(player.getUniqueId()))
			{
				Messages.sendMessage(player, RealEstate.instance.messages.msgErrorExitOfferCantAcceptSelf);
			}
			else if(Utils.makePayment(player.getUniqueId(), bt.exitOffer.offerBy, bt.exitOffer.price, true, false))
			{
				Messages.sendMessage(player, RealEstate.instance.messages.msgInfoExitOfferAcceptedBySelf, 
						claimType, RealEstate.econ.format(bt.exitOffer.price));

				UUID other = player.getUniqueId().equals(bt.owner) ? bt.buyer : bt.owner;
				String location = "[" + loc.getWorld().getName() + ", X: " + loc.getBlockX() + ", Y: " + loc.getBlockY() + 
					", Z: " + loc.getBlockZ() + "]";
				if(other != null)
				{
					OfflinePlayer otherP = Bukkit.getOfflinePlayer(other);
					if(otherP.isOnline())
					{
						Messages.sendMessage(otherP.getPlayer(), RealEstate.instance.messages.msgInfoExitOfferAcceptedByOther, 
								player.getName(), claimType, RealEstate.econ.format(bt.exitOffer.price), location);
					}
					else if(RealEstate.instance.config.cfgMailOffline && RealEstate.ess != null)
		        	{
		        		User u = RealEstate.ess.getUser(other);
						
		        		u.addMail(Messages.getMessage(RealEstate.instance.messages.msgInfoExitOfferAcceptedByOther,
								player.getName(), claimType, RealEstate.econ.format(bt.exitOffer.price), location));
		        	}
				}
				bt.exitOffer = null;
				claim.dropPermission(bt.buyer.toString());
				claim.managers.remove(bt.buyer.toString());
				GriefPrevention.instance.dataStore.saveClaim(claim);
				bt.buyer = null;
				bt.update();// eventual cancel is contained in here
			}
			// the make payment takes care of sending error if need be
		}
		
		@Subcommand("refuse")
		@Description("Refuses an offer to break an ongoing transaction")
		public void refuse(Player player)
		{
			Location loc = player.getLocation();
			Claim claim = GriefPrevention.instance.dataStore.getClaimAt(loc, false, null);
			BoughtTransaction bt = (BoughtTransaction)RealEstate.transactionsStore.getTransaction(claim);
			String claimType = claim.parent == null ? "claim" : "subclaim";
			if(bt.exitOffer == null)
			{
				Messages.sendMessage(player, RealEstate.instance.messages.msgErrorExitOfferNone);
			}
			else if(bt.exitOffer.offerBy.equals(player.getUniqueId()))
			{
				Messages.sendMessage(player, RealEstate.instance.messages.msgErrorExitOfferCantRefuseSelf);
			}
			else
			{
				bt.exitOffer = null;
				Messages.sendMessage(player, RealEstate.instance.messages.msgInfoExitOfferRejectedBySelf);
				UUID other = player.getUniqueId().equals(bt.owner) ? bt.buyer : bt.owner;
				String location = "[" + loc.getWorld().getName() + ", X: " + loc.getBlockX() + ", Y: " + loc.getBlockY() + 
					", Z: " + loc.getBlockZ() + "]";
				if(other != null)
				{
					OfflinePlayer otherP = Bukkit.getOfflinePlayer(other);
					if(otherP.isOnline())
					{
						Messages.sendMessage(otherP.getPlayer(), RealEstate.instance.messages.msgInfoExitOfferRejectedByOther, 
								player.getName(), claimType, location);
					}
					else if(RealEstate.instance.config.cfgMailOffline && RealEstate.ess != null)
		        	{
		        		User u = RealEstate.ess.getUser(other);
		        		u.addMail(Messages.getMessage(RealEstate.instance.messages.msgInfoExitOfferRejectedByOther,
								player.getName(), claimType, location));
		        	}
				}
			}
		}
		
		@Subcommand("cancel")
		@Description("Cancels an offer to break an ongoing transaction")
		public void cancel(Player player)
		{
			Location loc = player.getLocation();
			Claim claim = GriefPrevention.instance.dataStore.getClaimAt(loc, false, null);
			BoughtTransaction bt = (BoughtTransaction)RealEstate.transactionsStore.getTransaction(claim);
			String claimType = claim.parent == null ? "claim" : "subclaim";
			if(bt.exitOffer.offerBy.equals(player.getUniqueId()))
			{
				bt.exitOffer = null;
				Messages.sendMessage(player, RealEstate.instance.messages.msgInfoExitOfferCancelledBySelf);
				
				UUID other = player.getUniqueId().equals(bt.owner) ? bt.buyer : bt.owner;
				String location = "[" + loc.getWorld().getName() + ", X: " + loc.getBlockX() + ", Y: " + loc.getBlockY() + 
					", Z: " + loc.getBlockZ() + "]";
				if(other != null)
				{
					OfflinePlayer otherP = Bukkit.getOfflinePlayer(other);
					if(otherP.isOnline())
					{
						Messages.sendMessage(otherP.getPlayer(), RealEstate.instance.messages.msgInfoExitOfferCancelledByOther, 
								player.getName(), claimType, location);
					}
					else if(RealEstate.instance.config.cfgMailOffline && RealEstate.ess != null)
		        	{
		        		User u = RealEstate.ess.getUser(other);
		        		u.addMail(Messages.getMessage(RealEstate.instance.messages.msgInfoExitOfferCancelledByOther,
								player.getName(), claimType, location));
		        	}
				}
			}
			else
			{
				Messages.sendMessage(player, RealEstate.instance.messages.msgErrorExitOfferCantCancelOther);
			}
		}
	}
	
	@Subcommand("cancel")
	@Conditions("claimHasTransaction")
	@CommandPermission("realestate.admin")
	public static void cancelTransaction(Player player)
	{
		Location loc = player.getLocation();
		Claim claim = GriefPrevention.instance.dataStore.getClaimAt(loc, false, null);
		Transaction t = RealEstate.transactionsStore.getTransaction(claim);
		t.tryCancelTransaction(player, true);
	}
	
	@HelpCommand
	public static void onHelp(CommandSender sender, CommandHelp help)
	{
        help.showHelp();
	}
}
