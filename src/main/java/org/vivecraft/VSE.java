package org.vivecraft;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.craftbukkit.v1_21_R1.entity.CraftCreeper;
import org.bukkit.craftbukkit.v1_21_R1.entity.CraftEnderman;
import org.bukkit.craftbukkit.v1_21_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_21_R1.inventory.CraftItemStack;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.spigotmc.SpigotConfig;
import org.vivecraft.command.ConstructTabCompleter;
import org.vivecraft.command.ViveCommand;
import org.vivecraft.entities.CustomEndermanFreezeWhenLookedAt;
import org.vivecraft.entities.CustomEndermanLookForPlayerGoal;
import org.vivecraft.entities.CustomGoalSwell;
import org.vivecraft.listeners.VivecraftCombatListener;
import org.vivecraft.listeners.VivecraftItemListener;
import org.vivecraft.listeners.VivecraftNetworkListener;
import org.vivecraft.metrics.Metrics;
import org.vivecraft.utils.AimFixHandler;
import org.vivecraft.utils.Headshot;
import org.vivecraft.utils.MetadataHelper;

import net.milkbowl.vault.permission.Permission;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;

public class VSE extends JavaPlugin implements Listener {
	FileConfiguration config = getConfig();

	public final static String CHANNEL = "vivecraft:data";
	private final static String readurl = "https://raw.githubusercontent.com/jrbudda/Vivecraft_Spigot_Extensions/1.20/version.txt";
	private final static int bStatsId = 6931;

	public static Map<UUID, VivePlayer> vivePlayers = new HashMap<UUID, VivePlayer>();
	public static VSE me;

	private int sendPosDataTask = 0;
	public List<String> blocklist = new ArrayList<>();

	public boolean debug = false;

	@SuppressWarnings("deprecation")
	@Override
	public void onEnable() {
		super.onEnable();		
		me = this;

		if(getConfig().getBoolean("general.vive-crafting", true)){
			{
				ItemStack is = new ItemStack(Material.LEATHER_BOOTS);
				ItemMeta meta = is.getItemMeta();
				meta.setUnbreakable(true);
				meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
				((LeatherArmorMeta) meta).setColor(Color.fromRGB(9233775));
				is.setItemMeta(meta);		
				is = setLocalizedItemName(is,"vivecraft.item.jumpboots", "Jump Boots");
				ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(this, "jump_boots"),is);
				recipe.shape("B", "S");
				recipe.setIngredient('B', Material.LEATHER_BOOTS);
				recipe.setIngredient('S', Material.SLIME_BLOCK);
				Bukkit.addRecipe(recipe);
			}
			{
				ItemStack is = new ItemStack(Material.SHEARS);
				ItemMeta meta = is.getItemMeta();
				meta.setUnbreakable(true);
				meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
				is.setItemMeta(meta);
				is = setLocalizedItemName(is, "vivecraft.item.climbclaws", "Climb Claws");
				ShapedRecipe recipe = new ShapedRecipe( new NamespacedKey(this, "climb_claws"), is);
				recipe.shape("E E", "S S");
				recipe.setIngredient('E', Material.SPIDER_EYE);
				recipe.setIngredient('S', Material.SHEARS);
				Bukkit.addRecipe(recipe);
			}
		}
		try {
			Metrics metrics = new Metrics(this, bStatsId);    
			metrics.addCustomChart(new Metrics.AdvancedPie("vrplayers", new Callable<Map<String, Integer>>() {
				@Override
				public Map<String, Integer> call() throws Exception {
					int out = 0;
					for (Player p : Bukkit.getOnlinePlayers()) {
						//counts standing or seated players using VR
						if(isVive(p)) out++;
					} 	            	            	
					Map<String, Integer> valueMap = new HashMap<>();
					valueMap.put("VR", out);
					valueMap.put("NonVR", vivePlayers.size() - out);
					valueMap.put("Vanilla", Bukkit.getOnlinePlayers().size() - vivePlayers.size());
					return valueMap;
				}
			}));
		} catch (Exception e) {
			getLogger().warning("Could not start bStats metrics");
		}

		// Config Part
		config.options().copyDefaults(true);
		saveDefaultConfig();
		saveConfig();
		saveResource("config-instructions.yml", true);
		ConfigurationSection sec = config.getConfigurationSection("climbey");

		if(sec!=null){
			List<String> temp = sec.getStringList("blocklist");
			//make an attempt to validate these on the server for debugging.
			if(temp != null){
				for (String string : temp) {		
					if (BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(string)) == null) {
						getLogger().warning("Unknown climbey block name: " + string);
						continue;
					}
					blocklist.add(string);
				}
			}
		}				
		// end Config part

		getCommand("vive").setExecutor(new ViveCommand(this));
		getCommand("vse").setExecutor(new ViveCommand(this));
		getCommand("vive").setTabCompleter(new ConstructTabCompleter());
		getCommand("vse").setTabCompleter(new ConstructTabCompleter());

		getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, new VivecraftNetworkListener(this));
		getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);

		getServer().getPluginManager().registerEvents(this, this);
		getServer().getPluginManager().registerEvents(new VivecraftCombatListener(this), this);
		getServer().getPluginManager().registerEvents(new VivecraftItemListener(this), this);

		Headshot.init(this);

		if(getConfig().getBoolean("setSpigotConfig.enabled")){
			SpigotConfig.movedWronglyThreshold = getConfig().getDouble("setSpigotConfig.movedWronglyThreshold");
			SpigotConfig.movedTooQuicklyMultiplier = getConfig().getDouble("setSpigotConfig.movedTooQuickly");
		}

		debug = (getConfig().getBoolean("general.debug", false));

		sendPosDataTask = getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
			public void run() {
				sendPosData();
			}
		}, 20, 1);

		//check for any creepers and modify the fuse radius
		CheckAllEntities();

		vault = true;
		if(getServer().getPluginManager().getPlugin("Vault") == null || getServer().getPluginManager().getPlugin("Vault").isEnabled() == false) {
			getLogger().severe("Vault not found, permissions groups will not be set");
			vault = false;
		}	
		getServer().getScheduler().scheduleAsyncDelayedTask(this, new BukkitRunnable() {
			@Override
			public void run() {
				startUpdateCheck();
			}
		}, 1);
	}

	public static ItemStack setLocalizedItemName(ItemStack stack, String key, String fallback) {
		var nmsStack = CraftItemStack.asNMSCopy(stack);
		nmsStack.set(DataComponents.CUSTOM_NAME, Component.translatableWithFallback(key, fallback));
		return CraftItemStack.asBukkitCopy(nmsStack);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onEvent(CreatureSpawnEvent event) {
		if(!event.isCancelled()){
			EditEntity(event.getEntity());
		}
	}

	public void CheckAllEntities(){
		List<World> wrl = this.getServer().getWorlds();
		for(World world: wrl){
			for(Entity e: world.getLivingEntities()){
				EditEntity(e);
			}
		}
	}

	@SuppressWarnings("unchecked" )
	public void EditEntity(Entity entity){
		if(entity.getType() == EntityType.CREEPER){	
			Creeper e = ((CraftCreeper) entity).getHandle();
			AbstractCollection<WrappedGoal> goalB = (AbstractCollection<WrappedGoal>) Reflector.getFieldValue(Reflector.availableGoals, ((Mob)e).goalSelector);
			for(WrappedGoal b: goalB){
				if(b.getGoal() instanceof net.minecraft.world.entity.ai.goal.SwellGoal){//replace swell goal.
					goalB.remove(b);
					break;
				}
			}
			e.goalSelector.addGoal(2, new CustomGoalSwell(e));
		}
		else if(entity.getType() == EntityType.ENDERMAN){			
			EnderMan e = ((CraftEnderman) entity).getHandle();
			AbstractCollection<WrappedGoal> targets = (AbstractCollection<WrappedGoal>) Reflector.getFieldValue(Reflector.availableGoals, ((Mob)e).targetSelector);
			for(WrappedGoal b: targets){
				if(b.getPriority() == Reflector.enderManLookTargetPriority){ //replace PlayerWhoLookedAt target. Class is private cant use instanceof, check priority on all new versions.
					targets.remove(b);
					break;
				}
			}
			e.targetSelector.addGoal(Reflector.enderManLookTargetPriority, new CustomEndermanLookForPlayerGoal(e, e::isAngryAt));

			AbstractCollection<WrappedGoal> goals = (AbstractCollection<WrappedGoal>) Reflector.getFieldValue(Reflector.availableGoals, ((Mob)e).goalSelector);
			for(WrappedGoal b: goals){
				if(b.getPriority()==Reflector.enderManFreezePriority){//replace EndermanFreezeWhenLookedAt goal. Verify priority on new version.
					goals.remove(b);
					break;
				}
			}
			e.goalSelector.addGoal(Reflector.enderManFreezePriority, new CustomEndermanFreezeWhenLookedAt(e));
		}
	}

	public void sendPosData() {

		for (VivePlayer sendTo : vivePlayers.values()) {

			if (sendTo == null || sendTo.player == null || !sendTo.player.isOnline())
				continue; // dunno y but just in case.

			for (VivePlayer v : vivePlayers.values()) {

				if (v == sendTo || v == null || v.player == null || !v.player.isOnline() || v.player.getWorld() != sendTo.player.getWorld() || v.hmdData == null || v.controller0data == null || v.controller1data == null){
					continue;
				}

				double d = sendTo.player.getLocation().distanceSquared(v.player.getLocation());

				if (d < 256 * 256) {
					sendTo.player.sendPluginMessage(this, CHANNEL, v.getUberPacket());
				}
			}
		}
	}

	public void sendVRActiveUpdate(VivePlayer v) {
		if(v==null) return;
        var payload = v.getVRPacket();
		for (VivePlayer sendTo : vivePlayers.values()) {

			if (sendTo == null || sendTo.player == null || !sendTo.player.isOnline())
				continue; // dunno y but just in case.

			if (v == sendTo ||v.player == null || !v.player.isOnline()){
				continue;
			}

			sendTo.player.sendPluginMessage(this, CHANNEL, payload);
		}
	}

	@Override
	public void onDisable() {
		getServer().getScheduler().cancelTask(sendPosDataTask);
		super.onDisable();
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		vivePlayers.remove(event.getPlayer().getUniqueId());
		MetadataHelper.cleanupMetadata(event.getPlayer());

		if(getConfig().getBoolean("welcomemsg.enabled"))
			broadcastConfigString("welcomemsg.leaveMessage", event.getPlayer().getDisplayName());
	}

	@EventHandler
	public void onPlayerConnect(PlayerJoinEvent event) {
		final Player p = event.getPlayer();

		if (debug) getLogger().info(p.getName() + " Has joined the server");

		int t = getConfig().getInt("general.vive-only-kickwaittime", 200);
		if(t < 100) t = 100;
		if(t > 1000) t = 1000;

		if (debug) 
			getLogger().info("Checking " + event.getPlayer().getName() + " for Vivecraft");

		getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
			@Override
			public void run() {		
				if (p.isOnline()) {	
					boolean kick = false;

					if(vivePlayers.containsKey(p.getUniqueId())) {
						VivePlayer vp = VSE.vivePlayers.get(p.getUniqueId());
						if(debug)
							getLogger().info(p.getName() + " using: " + vp.version + " " + (vp.isVR() ? "VR" : "NONVR")  + " " + (vp.isSeated() ? "SEATED" : ""));
						if(!vp.isVR()) kick = true;
					} else {
						kick = true;
						if(debug)
							getLogger().info(p.getName() + " Vivecraft not detected");
					}	

					if(kick) {
						if (getConfig().getBoolean("general.vive-only")) {
							if (getConfig().getBoolean("general.allow-op") == false || !p.isOp()) {
								getLogger().info(p.getName() + " " + "got kicked for not using Vivecraft");
								p.kickPlayer(getConfig().getString("general.vive-only-kickmessage"));
							}						
							return;
						}
					}

					sendWelcomeMessage(p);
					setPermissionsGroup(p);
				} else {
					if (debug) 
						getLogger().info(p.getName() + " no longer online! ");
				}		
			}
		}, t);

		Connection netManager = (Connection) Reflector.getFieldValue(Reflector.connection, ((CraftPlayer)p).getHandle().connection); 
		netManager.channel.pipeline().addBefore("packet_handler", "vr_aim_fix", new AimFixHandler(netManager));
	}

	public void startUpdateCheck() {
		PluginDescriptionFile pdf = getDescription();
		String version = pdf.getVersion();
		getLogger().info("Version: " + version);
		if (getConfig().getBoolean("general.checkforupdate", true)) {
			try {
				getLogger().info("Checking for update...");
				URL url = new URL(readurl);
				BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
				String str;
				String updatemsg = null;
				while ((str = br.readLine()) != null) {
					String line = str;
					String[] bits = line.split(":");
					if(bits[0].trim().equalsIgnoreCase(version)){
						updatemsg = bits[1].trim();
						getLogger().info(updatemsg);
						break;
					}
				}
				br.close();
				if(updatemsg == null){
					getLogger().info("Version not found. Are you from the future?");
				}
			} catch (IOException e) {
				getLogger().severe("Error retrieving version list: " + e.getMessage());
			}
		}
	}

	public static boolean isVive(Player p){
		if(p == null) return false;
		if(vivePlayers.containsKey(p.getUniqueId())){
			return vivePlayers.get(p.getUniqueId()).isVR();
		}
		return false;
	}

	public static boolean isCompanion(Player p){
		if(p == null) return false;
		if(vivePlayers.containsKey(p.getUniqueId())){
			return !vivePlayers.get(p.getUniqueId()).isVR();
		}
		return false;
	}

	public void setPermissionsGroup(Player p) {
		if(!vault) return;	
		if(!getConfig().getBoolean("permissions.enabled")) return;

		Map<String, Boolean> groups = new HashMap<String, Boolean>();

		boolean isvive = isVive(p);
		boolean iscompanion = isCompanion(p);

		String g_vive = getConfig().getString("permissions.vivegroup");
		String g_classic = getConfig().getString("permissions.non-vivegroup");
		if (g_vive != null && !g_vive.trim().isEmpty())
			groups.put(g_vive, isvive);
		if (g_classic != null && !g_classic.trim().isEmpty())
			groups.put(g_classic, iscompanion);

		String g_freemove = getConfig().getString("permissions.freemovegroup");
		if (g_freemove != null && !g_freemove.trim().isEmpty()) {
			if (isvive) {
				groups.put(g_freemove, !vivePlayers.get(p.getUniqueId()).isTeleportMode);
			} else {
				groups.put(g_freemove, false);
			}
		}

		updatePlayerPermissionGroup(p, groups);

	}

	public boolean vault;

	public void updatePlayerPermissionGroup(Player p, Map<String, Boolean> groups) {
		try {	

			RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
			Permission perm = rsp.getProvider();

			if (perm == null) {
				getLogger().info("Permissions error: Registered permissions provider is null!");
				return;
			}			
			if(!perm.hasGroupSupport()) {
				getLogger().info("Permissions error: Permission plugin does not support groups.");
				return;
			}

			for (Map.Entry<String, Boolean> entry : groups.entrySet()) {
				if (entry.getValue()) {

					if (!perm.playerInGroup(null, p, entry.getKey())) {
						if (debug) 
							getLogger().info("Adding " + p.getName() + " to " + entry.getKey());
						boolean ret = perm.playerAddGroup(null, p, entry.getKey());
						if(!ret)
							getLogger().info("Failed adding " + p.getName() + " to " + entry.getKey() + ". Group may not exist.");

					}
				} else {
					if (perm.playerInGroup(null, p, entry.getKey())) {
						if (debug) 
							getLogger().info("Removing " + p.getName() + " from " + entry.getKey());
						boolean ret = perm.playerRemoveGroup(null, p, entry.getKey());
						if(!ret)
							getLogger().info("Failed removing " + p.getName() + " from " + entry.getKey()+ ". Group may not exist.");
					}
				}
			}

		} catch (Exception e) {
			getLogger().severe("Could not set player permission group: " + e.getMessage());
		}
	}

	boolean tryParseInt(String value) {  
		try {  
			Integer.parseInt(value);  
			return true;  
		} catch (NumberFormatException e) {  
			return false;  
		}  
	}

	public void broadcastConfigString(String node, String playername){
		String message = this.getConfig().getString(node);
		if(message == null || message.isEmpty()) return;
		String[] formats = message.replace("&player", playername).split("\\n");
		for(Player p : Bukkit.getOnlinePlayers()){
			for (String line : formats) {
				ViveCommand.sendMessage(line,p);
			}
		}
	}


	public void sendWelcomeMessage(Player p){
		if(!getConfig().getBoolean("welcomemsg.enabled"))return;

		VivePlayer vp = VSE.vivePlayers.get(p.getUniqueId());

		if(vp==null){
			broadcastConfigString("welcomemsg.welcomeVanilla", p.getDisplayName());
		} else {
			if(vp.isSeated())
				broadcastConfigString("welcomemsg.welcomeSeated", p.getDisplayName());
			else if (!vp.isVR())
				broadcastConfigString("welcomemsg.welcomenonVR", p.getDisplayName());
			else
				broadcastConfigString("welcomemsg.welcomeVR", p.getDisplayName());
		}
	}

	public static boolean isSeated(Player player){
		if(vivePlayers.containsKey(player.getUniqueId())){
			return vivePlayers.get(player.getUniqueId()).isSeated();
		}
		return false;
	}

	public static boolean isStanding(Player player){
		if(vivePlayers.containsKey(player.getUniqueId())){
			if(!vivePlayers.get(player.getUniqueId()).isSeated() && vivePlayers.get(player.getUniqueId()).isVR()) return true;
		}
		return false;
	}
}
