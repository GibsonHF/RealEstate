package me.EtienneDx.RealEstate;

import java.util.HashMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.EtienneDx.RealEstate.Transactions.ClaimRent;
import me.EtienneDx.RealEstate.Transactions.ClaimSell;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.PluginManager;

import me.EtienneDx.RealEstate.Transactions.Transaction;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;

public class REListener implements Listener
{
	public HashMap<UUID, Transaction> currentTransactions = new HashMap<>();
	void registerEvents()
	{
		PluginManager pm = RealEstate.instance.getServer().getPluginManager();

		pm.registerEvents(this, RealEstate.instance);
		//RealEstate.instance.getCommand("re").setExecutor(this);
	}

	@EventHandler
	public void onSignChange(SignChangeEvent event)
	{
		if(RealEstate.instance.config.cfgSellKeywords.contains(event.getLine(0).toLowerCase()) || 
				RealEstate.instance.config.cfgLeaseKeywords.contains(event.getLine(0).toLowerCase()) || 
				RealEstate.instance.config.cfgRentKeywords.contains(event.getLine(0).toLowerCase()) || 
				RealEstate.instance.config.cfgContainerRentKeywords.contains(event.getLine(0).toLowerCase()))
		{
			Player player = event.getPlayer();
			Location loc = event.getBlock().getLocation();

			Claim claim = GriefPrevention.instance.dataStore.getClaimAt(loc, false, null);
			if(claim == null)// must have something to sell
			{
				Messages.sendMessage(player, RealEstate.instance.messages.msgErrorSignNotInClaim);
				event.setCancelled(true);
				event.getBlock().breakNaturally();
				return;
			}
			if(RealEstate.transactionsStore.anyTransaction(claim))
			{
				Messages.sendMessage(player, RealEstate.instance.messages.msgErrorSignOngoingTransaction);
				event.setCancelled(true);
				event.getBlock().breakNaturally();
				return;
			}
			if(RealEstate.transactionsStore.anyTransaction(claim.parent))
			{
				Messages.sendMessage(player, RealEstate.instance.messages.msgErrorSignParentOngoingTransaction);
				event.setCancelled(true);
				event.getBlock().breakNaturally();
				return;
			}
			for(Claim c : claim.children)
			{
				if(RealEstate.transactionsStore.anyTransaction(c))
				{
					Messages.sendMessage(player, RealEstate.instance.messages.msgErrorSignSubclaimOngoingTransaction);
					event.setCancelled(true);
					event.getBlock().breakNaturally();
					return;
				}
			}

			// empty is considered a wish to sell
			if(RealEstate.instance.config.cfgSellKeywords.contains(event.getLine(0).toLowerCase()))
			{
				if(!RealEstate.instance.config.cfgEnableSell)
				{
					Messages.sendMessage(player, RealEstate.instance.messages.msgErrorSignSellingDisabled);
					event.setCancelled(true);
					event.getBlock().breakNaturally();
					return;
				}

				String type = claim.parent == null ? "claim" : "subclaim";
				String typeDisplay = claim.parent == null ?
						RealEstate.instance.messages.keywordClaim : RealEstate.instance.messages.keywordSubclaim;
				if(!RealEstate.perms.has(player, "realestate." + type + ".sell"))
				{
					Messages.sendMessage(player, RealEstate.instance.messages.msgErrorSignNoSellPermission, typeDisplay);
					event.setCancelled(true);
					event.getBlock().breakNaturally();
					return;
				}

				// check for a valid price
				double price;
				try
				{
					price = getDouble(event, 1, RealEstate.instance.config.cfgPriceSellPerBlock * claim.getArea());
				}
				catch (NumberFormatException e)
				{
					Messages.sendMessage(player, RealEstate.instance.messages.msgErrorInvalidNumber, event.getLine(1));
					event.setCancelled(true);
					event.getBlock().breakNaturally();
					return;
				}
				if(price <= 0)
				{
					Messages.sendMessage(player, RealEstate.instance.messages.msgErrorNegativePrice, event.getLine(1));
					event.setCancelled(true);
					event.getBlock().breakNaturally();
					return;
				}
				if((price%1)!=0 && !RealEstate.instance.config.cfgUseDecimalCurrency) //if the price has a decimal number AND Decimal currency is disabled
				{
					Messages.sendMessage(player, RealEstate.instance.messages.msgErrorNonIntegerPrice, event.getLine(1));
					event.setCancelled(true);
					event.getBlock().breakNaturally();
					return;
				}
				
				if(claim.isAdminClaim())
				{
					if(!RealEstate.perms.has(player, "realestate.admin"))// admin may sell admin claims
					{
						Messages.sendMessage(player, RealEstate.instance.messages.msgErrorSignNoAdminSellPermission, typeDisplay);
						event.setCancelled(true);
						event.getBlock().breakNaturally();
						return;
					}
				}
				else if(type.equals("claim") && !player.getUniqueId().equals(claim.ownerID))// only the owner may sell his claim
				{
					Messages.sendMessage(player, RealEstate.instance.messages.msgErrorSignNotOwner, typeDisplay);
					event.setCancelled(true);
					event.getBlock().breakNaturally();
					return;
				}

				// we should be good to sell it now
				//event.setCancelled(true);// need to cancel the event, so we can update the sign elsewhere
				RealEstate.transactionsStore.sell(claim, claim.isAdminClaim() ? null : player, price, event.getBlock().getLocation());
			}
			else if(RealEstate.instance.config.cfgRentKeywords.contains(event.getLine(0).toLowerCase()) ||
					RealEstate.instance.config.cfgContainerRentKeywords.contains(event.getLine(0).toLowerCase()))// we want to rent it
			{
				if(!RealEstate.instance.config.cfgEnableRent)
				{
					Messages.sendMessage(player, RealEstate.instance.messages.msgErrorSignRentingDisabled);
					event.setCancelled(true);
					event.getBlock().breakNaturally();
					return;
				}
				String type = claim.parent == null ? "claim" : "subclaim";
				String typeDisplay = claim.parent == null ?
						RealEstate.instance.messages.keywordClaim : RealEstate.instance.messages.keywordSubclaim;
				if(!RealEstate.perms.has(player, "realestate." + type + ".rent"))
				{
					Messages.sendMessage(player, RealEstate.instance.messages.msgErrorSignNoRentPermission, typeDisplay);
					event.setCancelled(true);
					event.getBlock().breakNaturally();
					return;
				}

				// check for a valid price
				double price;
				try
				{
					price = getDouble(event, 1, RealEstate.instance.config.cfgPriceRentPerBlock * claim.getArea());
				}
				catch (NumberFormatException e)
				{
					Messages.sendMessage(player, RealEstate.instance.messages.msgErrorInvalidNumber, event.getLine(1));
					event.setCancelled(true);
					event.getBlock().breakNaturally();
					return;
				}
				if(price <= 0)
				{
					Messages.sendMessage(player, RealEstate.instance.messages.msgErrorNegativePrice, event.getLine(1));
					event.setCancelled(true);
					event.getBlock().breakNaturally();
					return;
				}
				if((price%1)!=0 && !RealEstate.instance.config.cfgUseDecimalCurrency) //if the price has a decimal number AND Decimal currency is disabled
				{
					Messages.sendMessage(player, RealEstate.instance.messages.msgErrorNonIntegerPrice, event.getLine(1));
					event.setCancelled(true);
					event.getBlock().breakNaturally();
					return;
				}

				if(event.getLine(2).isEmpty())
				{
					event.setLine(2, RealEstate.instance.config.cfgRentTime);
				}
				int duration = parseDuration(event.getLine(2));
				if(duration == 0)
				{
					Messages.sendMessage(player, RealEstate.instance.messages.msgErrorInvalidDuration, event.getLine(2),
						"10 weeks",
						"3 days",
						"1 week 3 days");
					event.setCancelled(true);
					event.getBlock().breakNaturally();
					return;
				}
				int rentPeriods = 1;
				if(RealEstate.instance.config.cfgEnableRentPeriod)
				{
					if(event.getLine(3).isEmpty())
					{
						event.setLine(3, "1");
					}
					try
					{
						rentPeriods = Integer.parseInt(event.getLine(3));
					}
					catch (NumberFormatException e)
					{
						Messages.sendMessage(player, RealEstate.instance.messages.msgErrorInvalidNumber, event.getLine(3));
						event.setCancelled(true);
						event.getBlock().breakNaturally();
						return;
					}
					if(rentPeriods <= 0)
					{
						Messages.sendMessage(player, RealEstate.instance.messages.msgErrorNegativeNumber, event.getLine(3));
						event.setCancelled(true);
						event.getBlock().breakNaturally();
						return;
					}
				}

				if(claim.isAdminClaim())
				{
					if(!RealEstate.perms.has(player, "realestate.admin"))// admin may sell admin claims
					{
						Messages.sendMessage(player, RealEstate.instance.messages.msgErrorSignNoAdminRentPermission, typeDisplay);
						event.setCancelled(true);
						event.getBlock().breakNaturally();
						return;
					}
				}
				else if(type.equals("claim") && !player.getUniqueId().equals(claim.ownerID))// only the owner may sell his claim
				{
					Messages.sendMessage(player, RealEstate.instance.messages.msgErrorSignNotOwner, typeDisplay);
					event.setCancelled(true);
					event.getBlock().breakNaturally();
					return;
				}

				// all should be good, we can create the rent
				//event.setCancelled(true);
				RealEstate.transactionsStore.rent(claim, player, price, event.getBlock().getLocation(), duration, rentPeriods,
						RealEstate.instance.config.cfgRentKeywords.contains(event.getLine(0).toLowerCase()));
			}
			else if(RealEstate.instance.config.cfgLeaseKeywords.contains(event.getLine(0).toLowerCase()))// we want to lease it
			{
				if(!RealEstate.instance.config.cfgEnableLease)
				{
					Messages.sendMessage(player, RealEstate.instance.messages.msgErrorSignLeasingDisabled);
					event.setCancelled(true);
					event.getBlock().breakNaturally();
					return;
				}
				String type = claim.parent == null ? "claim" : "subclaim";
				String typeDisplay = claim.parent == null ?
					RealEstate.instance.messages.keywordClaim :
					RealEstate.instance.messages.keywordSubclaim;
				if(!RealEstate.perms.has(player, "realestate." + type + ".lease"))
				{
					Messages.sendMessage(player, RealEstate.instance.messages.msgErrorSignNoLeasePermission, typeDisplay);
					event.setCancelled(true);
					event.getBlock().breakNaturally();
					return;
				}

				// check for a valid price
				double price;
				try
				{
					price = getDouble(event, 1, RealEstate.instance.config.cfgPriceLeasePerBlock * claim.getArea());
				}
				catch (NumberFormatException e)
				{
					Messages.sendMessage(player, RealEstate.instance.messages.msgErrorInvalidNumber, event.getLine(1));
					event.setCancelled(true);
					event.getBlock().breakNaturally();
					return;
				}
				if(price <= 0)
				{
					Messages.sendMessage(player, RealEstate.instance.messages.msgErrorNegativePrice, event.getLine(1));
					event.setCancelled(true);
					event.getBlock().breakNaturally();
					return;
				}
				if((price%1)!=0 && !RealEstate.instance.config.cfgUseDecimalCurrency) //if the price has a decimal number AND Decimal currency is disabled
				{
					Messages.sendMessage(player, RealEstate.instance.messages.msgErrorNonIntegerPrice, event.getLine(1));
					event.setCancelled(true);
					event.getBlock().breakNaturally();
					return;
				}

				if(event.getLine(2).isEmpty())
				{
					event.setLine(2, "" + RealEstate.instance.config.cfgLeasePayments);
				}
				int paymentsCount;
				try
				{
					paymentsCount = Integer.parseInt(event.getLine(2));
				}
				catch(Exception e)
				{
					Messages.sendMessage(player, RealEstate.instance.messages.msgErrorInvalidNumber, event.getLine(2));
					event.setCancelled(true);
					event.getBlock().breakNaturally();
					return;
				}

				if(event.getLine(3).isEmpty())
				{
					event.setLine(3, RealEstate.instance.config.cfgLeaseTime);
				}
				int frequency = parseDuration(event.getLine(3));
				if(frequency == 0)
				{
					Messages.sendMessage(player, RealEstate.instance.messages.msgErrorInvalidDuration, event.getLine(3),
						"10 weeks",
						"3 days",
						"1 week 3 days");
					event.setCancelled(true);
					event.getBlock().breakNaturally();
					return;
				}

				if(claim.isAdminClaim())
				{
					if(!RealEstate.perms.has(player, "realestate.admin"))// admin may sell admin claims
					{
						Messages.sendMessage(player, RealEstate.instance.messages.msgErrorSignNoAdminLeasePermission, typeDisplay);
						event.setCancelled(true);
						event.getBlock().breakNaturally();
						return;
					}
				}
				else if(type.equals("claim") && !player.getUniqueId().equals(claim.ownerID))// only the owner may sell his claim
				{
					Messages.sendMessage(player, RealEstate.instance.messages.msgErrorSignNotOwner, typeDisplay);
					event.setCancelled(true);
					event.getBlock().breakNaturally();
					return;
				}

				// all should be good, we can create the rent
				//event.setCancelled(true);
				RealEstate.transactionsStore.lease(claim, player, price, event.getBlock().getLocation(), frequency, paymentsCount);
			}
		}
	}

	private int parseDuration(String line)
	{
		Pattern p = Pattern.compile("^(?:(?<weeks>\\d{1,2}) ?w(?:eeks?)?)? ?(?:(?<days>\\d{1,2}) ?d(?:ays?)?)?$", Pattern.CASE_INSENSITIVE);
		Matcher m = p.matcher(line);
		if(!line.isEmpty() && m.matches()) 
		{
			int ret = 0;
			if(m.group("weeks") != null)
				ret += 7 * Integer.parseInt(m.group("weeks"));
			if(m.group("days") != null)
				ret += Integer.parseInt(m.group("days"));
			return ret;
		}
		return 0;
	}

	@EventHandler
	public void onInventoryClick(InventoryClickEvent event) {
		ItemStack clickedItem = event.getCurrentItem();
		Player player = (Player) event.getWhoClicked();
		if (clickedItem == null) return;
		if (event.getWhoClicked() instanceof Player) {
			if (event.getView().getTitle().equals("Real Estate Listings")) {
				event.setCancelled(true);

				if (clickedItem != null && clickedItem.getType() == Material.OAK_SIGN) {
					// Extract Claim ID from the clicked item
					String displayName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
					String claimIDStr = displayName.replace("Claim ID: ", "").trim();
					long claimID = Long.parseLong(claimIDStr);

					Transaction tr = null;
					if (event.isLeftClick()) {

						Claim claim = GriefPrevention.instance.dataStore.getClaim(claimID);
						if (claim != null) {
							Location location = claim.getGreaterBoundaryCorner(); // or choose another location within the claim
							// Ensure the player is above the ground
							while (location.getBlock().getType() != Material.AIR || location.add(0, 1, 0).getBlock().getType() != Material.AIR) {
								location.add(0, 1, 0); // Move one block up
							}

							player.teleport(location);
							player.sendMessage(ChatColor.GREEN + "Teleported to claim ID: " + claimIDStr);

						} else {
							player.sendMessage(ChatColor.RED + "Claim ID: " + claimIDStr + " not found.");
						}
					}


				}
			}
		}

	}

	private double getDouble(SignChangeEvent event, int line, double defaultValue) throws NumberFormatException
	{
		if(event.getLine(line).isEmpty())// if no price precised, make it the default one
		{
			event.setLine(line, Double.toString(defaultValue));
		}
		return Double.parseDouble(event.getLine(line));
	}

	@EventHandler
	public void onPlayerInteract(PlayerInteractEvent event)
	{
		if(event.getAction().equals(Action.LEFT_CLICK_BLOCK) && event.getHand().equals(EquipmentSlot.HAND) &&
				event.getClickedBlock().getState() instanceof Sign)
		{
			Sign sign = (Sign)event.getClickedBlock().getState();
			RealEstate.instance.log.info(sign.getLine(0));
			// it is a real estate sign
			if(ChatColor.stripColor(sign.getLine(0)).equalsIgnoreCase(ChatColor.stripColor(
				Messages.getMessage(RealEstate.instance.config.cfgSignsHeader, false))))
			{
				Player player = event.getPlayer();
				Claim claim = GriefPrevention.instance.dataStore.getClaimAt(event.getClickedBlock().getLocation(), false, null);

				if(!RealEstate.transactionsStore.anyTransaction(claim))
				{
					Messages.sendMessage(player, RealEstate.instance.messages.msgErrorSignNoTransaction);
					event.getClickedBlock().breakNaturally();
					event.setCancelled(true);
					return;
				}

				Transaction tr = RealEstate.transactionsStore.getTransaction(claim);
					tr.interact(player);
			}
		}
	}

	@EventHandler
	public void onBreakBlock(BlockBreakEvent event)
	{
		if(event.getBlock().getState() instanceof Sign)
		{
			Claim claim = GriefPrevention.instance.dataStore.getClaimAt(event.getBlock().getLocation(), false, null);
			if(claim != null)
			{
				Transaction tr = RealEstate.transactionsStore.getTransaction(claim);
				if(tr != null && event.getBlock().equals(tr.getHolder()))
				{
					if(event.getPlayer() != null && tr.getOwner() != null  && !event.getPlayer().getUniqueId().equals(tr.getOwner()) && 
							!RealEstate.perms.has(event.getPlayer(), "realestate.destroysigns"))
					{
						Messages.sendMessage(event.getPlayer(), RealEstate.instance.messages.msgErrorSignNotAuthor);
						event.setCancelled(true);
						return;
					}
					else if(event.getPlayer() != null && tr.getOwner() == null && !RealEstate.perms.has(event.getPlayer(), "realestate.admin"))
					{
						Messages.sendMessage(event.getPlayer(), RealEstate.instance.messages.msgErrorSignNotAdmin);
						event.setCancelled(true);
						return;
					}
					// the sign has been destroy, we can try to cancel the transaction
					if(!tr.tryCancelTransaction(event.getPlayer()))
					{
						event.setCancelled(true);
					}
				}
			}
		}
	}
}
