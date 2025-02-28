package org.vivecraft.utils;

import org.bukkit.craftbukkit.v1_21_R1.entity.CraftEntity;
import org.bukkit.entity.Player;
import org.vivecraft.Reflector;
import org.vivecraft.VSE;
import org.vivecraft.VivePlayer;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.DataItem;
import net.minecraft.world.entity.Pose;


public class PoseOverrider {
	@SuppressWarnings("unchecked")
	public static void injectPlayer(Player player) {
			EntityDataAccessor<Pose> poseObj = (EntityDataAccessor<Pose>) Reflector.getFieldValue(Reflector.Entity_Data_Pose, player);
			SynchedEntityData dataWatcher = ((CraftEntity) player).getHandle().getEntityData();
			SynchedEntityData.DataItem<?>[] entries = (DataItem<?>[]) Reflector.getFieldValue(Reflector.SynchedEntityData_itemsById, dataWatcher);
			InjectedDataWatcherItem item = new InjectedDataWatcherItem(poseObj, Pose.STANDING, player);
			if(entries.length-1 >= poseObj.id())
				entries[poseObj.id()] = item;		
	}

	public static class InjectedDataWatcherItem extends SynchedEntityData.DataItem<Pose> {
		protected final Player player;

		public InjectedDataWatcherItem(EntityDataAccessor<Pose> datawatcherobject, Pose t0, Player player) {
			super(datawatcherobject, t0);
			this.player = player;
		}

		@Override
		public void setValue(Pose pose) {
			VivePlayer vp = VSE.vivePlayers.get(player.getUniqueId());
			if (vp != null && vp.isVR() && vp.crawling)
				super.setValue(Pose.SWIMMING);
			else
				super.setValue(pose);
		}
	}
}
