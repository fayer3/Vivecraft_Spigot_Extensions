package org.vivecraft.listeners;

import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.vivecraft.VSE;
import org.vivecraft.VivePlayer;
import org.vivecraft.utils.Headshot;

import net.minecraft.server.v1_12_R1.Vec3D;


public class VivecraftCombatListener implements Listener{

	private VSE vse;
	
	public VivecraftCombatListener(VSE plugin){
		this.vse = plugin;
	}
 	   @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
	   public void onProjectileLaunch(ProjectileLaunchEvent event) {
		   //position all projectiles correctly.
		   
		   final Projectile proj = event.getEntity();
		   if (!(proj.getShooter() instanceof Player) || !VSE.isVive((Player) proj.getShooter()))
			   return;

		    Player pl = (Player)proj.getShooter();
		    final VivePlayer vp = (VivePlayer)VSE.vivePlayers.get(pl.getUniqueId());
		   
		   final int hand = proj instanceof CraftArrow ? 1 : 0;

		   //TODO: check for seated mode.
		   
		   if ((vp == null) && (this.vse.getConfig().getBoolean("general.debug"))) {
			   vse.getLogger().warning(" Error on projectile launch!");
		   }
		   
		   //this only works if the incoming speed is at max (based! on draw time)
		   //TODO: properly scale in all cases.
		   
		   if(proj.getType() == EntityType.ARROW && vp.getDraw() != 0) {
			   proj.setVelocity(proj.getVelocity().multiply(vp.getDraw()));  
		   }
	        vse.getServer().getScheduler().scheduleSyncDelayedTask(vse, new Runnable() {
	            @Override
	            public void run() {
	               Vec3D aim = vp.getControllerDir(hand);
	               Location pos = vp.getControllerPos(hand);
	     		   proj.teleport(new Location(proj.getWorld(), pos.getX() + aim.x*0.6f, pos.getY()+aim.y*0.6f, pos.getZ()+aim.z*0.6f));
	            }
	        }, 1);
	   }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onProjectileHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Arrow && event.getEntity() instanceof LivingEntity) {
            final Arrow arrow = (Arrow) event.getDamager();
            LivingEntity target = (LivingEntity) event.getEntity();
            boolean headshot = Headshot.isHeadshot(target, arrow);

            if (!(arrow.getShooter() instanceof Player) || !VSE.isVive((Player) arrow.getShooter()))
                return;
            Player pl = (Player)arrow.getShooter();
            VivePlayer vp = (VivePlayer)VSE.vivePlayers.get(pl.getUniqueId());

            if(!vp.isSeated()){
                if(headshot){
                    event.setDamage(event.getDamage() * vse.getConfig().getDouble("bow.standingheadshotmultiplier"));
                }else{
                    event.setDamage(event.getDamage() * vse.getConfig().getDouble("bow.standingmultiplier"));
                }
            }else{
                if(headshot){
                    event.setDamage(event.getDamage() * vse.getConfig().getDouble("bow.seatedheadshotmultiplier"));
                }else{
                    event.setDamage(event.getDamage() * vse.getConfig().getDouble("bow.seatedmultiplier"));
                }
            }

        }
    }
    
    public boolean usingVR(Player player){
    	if(VSE.vivePlayers.containsKey(player.getUniqueId())){
    		if(!VSE.vivePlayers.get(player.getUniqueId()).isVR()){
    			return false;
    		}
    		return true;
    	}
    	return false;
    }
    
    public boolean isSeated(Player player){
    	if(VSE.vivePlayers.containsKey(player.getUniqueId())){
    		return VSE.vivePlayers.get(player.getUniqueId()).isSeated();
    	}
    	return false;
    }
    
    public boolean isStanding(Player player){
    	if(VSE.vivePlayers.containsKey(player.getUniqueId())){
    		if(!VSE.vivePlayers.get(player.getUniqueId()).isSeated() && VSE.vivePlayers.get(player.getUniqueId()).isVR()) return true;
    	}
    	return false;
    }
    
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
	public void onDamage(EntityDamageByEntityEvent e) {
		final Entity damager = e.getDamager();
		final Entity damaged = e.getEntity();

		if (damager instanceof Player) {
			if (damaged instanceof Player) {
				Player attacker = (Player) damager;
				Player victim = (Player) damager;

				if (!vse.getConfig().getBoolean("pvp.VRvsVR",true)) {
					if (usingVR(attacker) && usingVR(victim)) {
						if (isStanding(attacker) && isStanding(victim)) {
							e.setCancelled(true);
						}
					}
				}

				if (!vse.getConfig().getBoolean("pvp.VRvsNONVR", true)) {
					if ((usingVR(attacker) && !usingVR(victim)) || (usingVR(victim) && !usingVR(attacker))) {
						e.setCancelled(true);
					}
				}
				
				if (!vse.getConfig().getBoolean("pvp.SEATEDVRvsNONVR", true)) {
					if(((usingVR(attacker) && isSeated(attacker)) && !usingVR(victim)) || ((usingVR(victim) && isSeated(victim)) && !usingVR(attacker))){
						e.setCancelled(true);
					}
				}

				if (!vse.getConfig().getBoolean("pvp.VRvsSEATEDVR", true)) {
					if (usingVR(attacker) && usingVR(victim)) {
						if ((isSeated(attacker) && isStanding(victim)) || (isSeated(victim) && isStanding(attacker))) {
							e.setCancelled(true);
						}
					}
				}

			}
		}
	}
    
}