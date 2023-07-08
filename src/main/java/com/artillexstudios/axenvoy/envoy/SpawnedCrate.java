package com.artillexstudios.axenvoy.envoy;

import com.artillexstudios.axenvoy.AxEnvoyPlugin;
import com.artillexstudios.axenvoy.rewards.CommandReward;
import com.artillexstudios.axenvoy.utils.FallingBlockChecker;
import com.artillexstudios.axenvoy.utils.StringUtils;
import com.artillexstudios.axenvoy.utils.Utils;
import me.hsgamer.unihologram.common.api.Hologram;
import me.hsgamer.unihologram.common.line.TextHologramLine;
import me.hsgamer.unihologram.spigot.SpigotHologramProvider;
import me.hsgamer.unihologram.spigot.common.hologram.extra.PlayerVisibility;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SpawnedCrate {
    public static final SpigotHologramProvider provider = new SpigotHologramProvider();
    private final Envoy parent;
    private final Crate handle;
    private Location finishLocation;
    private FallingBlock fallingBlock;
    private Hologram<Location> hologram;

    public SpawnedCrate(@NotNull Envoy parent, @NotNull Crate handle, @NotNull Location location) {
        this.parent = parent;
        this.handle = handle;
        this.finishLocation = location;

        location.getWorld().getChunkAtAsync(location).thenAccept(chunk -> {
            chunk.addPluginChunkTicket(AxEnvoyPlugin.getInstance());
            this.parent.getSpawnedCrates().add(this);

            if (!handle.isFallingBlock()) {
                land(location);
                return;
            }

            Location spawnAt = location.clone();
            spawnAt.add(0, this.handle.getFallingBlockHeight(), 0);
            fallingBlock = location.getWorld().spawnFallingBlock(spawnAt, this.handle.getFallingBlockType().createBlockData());
            fallingBlock.setDropItem(false);
            FallingBlockChecker.addToCheck(this);
            fallingBlock.setVelocity(new Vector(0, handle.getFallingBlockSpeed(), 0));
        });
    }

    public void land(@NotNull Location location) {
        this.finishLocation = location;
        location.getWorld().getBlockAt(location).setType(this.handle.getMaterial());
        this.spawnHologram(location);
    }

    private void spawnHologram(@NotNull Location location) {
        if (!handle.isHologram()) return;
        Location hologramLocation = location.clone().getBlock().getLocation();
        hologramLocation.add(0.5, handle.getHologramHeight(), 0.5);

        hologram = provider.createHologram("axenvoy-%s".formatted(Utils.serializeLocation(hologramLocation).replace(";", "")), hologramLocation);
        hologram.init();
        for (String hologramLine : handle.getHologramLines()) {
            hologram.addLine(new TextHologramLine(StringUtils.formatToString(hologramLine)));
        }

        if (hologram instanceof PlayerVisibility v) {
            v.showAll();
        }
    }

    public void claim(@Nullable Player player, Envoy envoy) {
        this.claim(player, envoy, true);
    }

    public void claim(@Nullable Player player, Envoy envoy, boolean remove) {
        if (player != null) {
            CommandReward reward = Utils.randomReward(this.handle.getRewards());
            reward.execute(player);
        }

        finishLocation.getWorld().getBlockAt(finishLocation).setType(Material.AIR);
        if (hologram != null) {
            hologram.clear();
        }

        if (remove) {
            this.parent.getSpawnedCrates().remove(this);
        }

        if (envoy != null) {
            boolean broadcast;
            if (this.handle.isBroadcastCollect() == TriState.NOT_SET) {
                broadcast = envoy.isBroadcastCollect();
            } else {
                broadcast = Boolean.TRUE.equals(this.handle.isBroadcastCollect().toBoolean());
            }

            if (broadcast && player != null) {
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    onlinePlayer.sendMessage(envoy.getMessage("prefix").append(envoy.getMessage("collect").replaceText(text -> {
                        text.match("%crate%");
                        text.replacement(StringUtils.formatToString(this.handle.getDisplayName()));
                    }).replaceText(replace -> {
                        replace.match("%amount%");
                        replace.replacement(String.valueOf(envoy.getSpawnedCrates().size()));
                    }).replaceText(replace -> {
                        replace.match("%player%");
                        replace.replacement(player.getName());
                    })));
                }
            }

            if (this.parent.getSpawnedCrates().isEmpty()) {
                envoy.setActive(false);
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    onlinePlayer.sendMessage(envoy.getMessage("prefix").append(envoy.getMessage("ended")));
                }
            }
        }
    }

    public Crate getHandle() {
        return handle;
    }

    public FallingBlock getFallingBlock() {
        return fallingBlock;
    }

    public void setFallingBlock(FallingBlock fallingBlock) {
        this.fallingBlock = fallingBlock;
    }

    public Location getFinishLocation() {
        return finishLocation;
    }
}