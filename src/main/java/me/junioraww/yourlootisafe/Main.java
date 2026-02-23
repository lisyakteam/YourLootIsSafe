package me.junioraww.yourlootisafe;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public final class Main extends JavaPlugin implements Listener {

  private NamespacedKey lootKey;

  @Override
  public void onEnable() {
    this.lootKey = new NamespacedKey(this, "stored_inventory");
    getServer().getPluginManager().registerEvents(this, this);

    Bukkit.getScheduler().runTaskTimer(this, () -> {
      for (org.bukkit.World world : Bukkit.getWorlds()) {
        for (Mannequin mannequin : world.getEntitiesByClass(Mannequin.class)) {
          if (mannequin.getPersistentDataContainer().has(lootKey, PersistentDataType.STRING)) {

            if (mannequin.getLocation().add(0, 0.5, 0).getBlock().isLiquid()) {
              mannequin.setVelocity(mannequin.getVelocity().setY(0.1));
            }
          }
        }
      }
    }, 0L, 2L);
  }

  @EventHandler
  public void onPlayerDeath(PlayerDeathEvent event) {
    if (event.getDrops().isEmpty()) return;

    Player player = event.getEntity();
    Location loc = player.getLocation();

    if (loc.getY() < loc.getWorld().getMinHeight()) {
      event.setKeepInventory(true);
      return;
    }

    List<ItemStack> drops = new ArrayList<>(event.getDrops());
    event.getDrops().clear();

    java.util.Iterator<ItemStack> iterator = drops.iterator();
    while (iterator.hasNext()) {
      ItemStack item = iterator.next();
      if (item.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable damageable) {
        int maxDurability;

        if (damageable.hasMaxDamage()) maxDurability = damageable.getMaxDamage();
        else if (item.getType().getMaxDurability() != 0) maxDurability = item.getType().getMaxDurability();
        else maxDurability = damageable.getDamage();

        Bukkit.getLogger().info("dura " + maxDurability + " " + damageable.hasMaxDamage());

        if (maxDurability > 0) {
          int damageToAdd = maxDurability / (java.util.concurrent.ThreadLocalRandom.current().nextBoolean() ? 3 : 4);
          int finalDamage = damageable.getDamage() + damageToAdd;

          if (finalDamage >= maxDurability) {
            iterator.remove();
          } else {
            damageable.setDamage(finalDamage);
            item.setItemMeta(damageable);
          }
        }
      }
    }

    drops.remove(java.util.concurrent.ThreadLocalRandom.current().nextInt(drops.size()));

    Mannequin mannequin = (Mannequin) loc.getWorld().spawnEntity(loc, EntityType.MANNEQUIN);

    mannequin.setPose(Pose.SWIMMING);
    mannequin.setAI(false);

    mannequin.setGravity(true);
    mannequin.setRemoveWhenFarAway(false);
    mannequin.setPersistent(true);

    Attribute health = Attribute.MAX_HEALTH;
    mannequin.registerAttribute(health);
    mannequin.getAttribute(health).setBaseValue(0.1);
    mannequin.setHealth(0.1);

    ResolvableProfile resolvableProfile = ResolvableProfile.resolvableProfile()
            .name(player.getName())
            .addProperties(player.getPlayerProfile().getProperties())
            .build();

    mannequin.setProfile(resolvableProfile);

    Component name = Component.text("☠ " + player.getName()).color(TextColor.color(255, 0, 0));
    Component description = Component.text("Вещи сохранены").color(TextColor.color(0, 255, 0));
    mannequin.customName(name);
    mannequin.setCustomNameVisible(true);
    mannequin.setDescription(description);
    mannequin.setGlowing(true);

    org.bukkit.inventory.EntityEquipment mInv = mannequin.getEquipment();
    mInv.clear();

    for (ItemStack item : drops) {
      String materialName = item.getType().name();
      if (materialName.endsWith("_HELMET") && mInv.getHelmet() == null) mInv.setHelmet(item);
      else if (materialName.endsWith("_CHESTPLATE") && mInv.getChestplate() == null) mInv.setChestplate(item);
      else if (materialName.endsWith("_LEGGINGS") && mInv.getLeggings() == null) mInv.setLeggings(item);
      else if (materialName.endsWith("_BOOTS") && mInv.getBoots() == null) mInv.setBoots(item);
      else if (mInv.getItemInMainHand().getType().isAir()) mInv.setItemInMainHand(item);
    }

    String serialized = itemsToBase64(drops);
    mannequin.getPersistentDataContainer().set(lootKey, PersistentDataType.STRING, serialized);
  }

  @EventHandler
  public void onMannequinDamage(EntityDamageEvent event) {
    if (!(event.getEntity() instanceof Mannequin mannequin)) return;

    if (!mannequin.getPersistentDataContainer().has(lootKey, PersistentDataType.STRING)) return;

    if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent entityEvent) {
      if (entityEvent.getDamager() instanceof Player) {
        return;
      }
    }

    event.setCancelled(true);
  }

  @EventHandler
  public void onMannequinCombust(org.bukkit.event.entity.EntityCombustEvent event) {
    if (event.getEntity() instanceof Mannequin mannequin) {
      if (mannequin.getPersistentDataContainer().has(lootKey, PersistentDataType.STRING)) {
        event.setCancelled(true);
      }
    }
  }

  @EventHandler
  public void onMannequinDeath(EntityDeathEvent event) {
    if (event.getEntity() instanceof Mannequin mannequin) {
      String data = mannequin.getPersistentDataContainer().get(lootKey, PersistentDataType.STRING);

      if (data != null) {
        List<ItemStack> items = itemsFromBase64(data);
        for (ItemStack item : items) {
          if (item != null) {
            mannequin.getWorld().dropItemNaturally(mannequin.getLocation(), item);
          }
        }
      }
    }
  }

  private String itemsToBase64(List<ItemStack> items) {
    try {
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      BukkitObjectOutputStream dataStream = new BukkitObjectOutputStream(outputStream);
      dataStream.writeInt(items.size());
      for (ItemStack item : items) dataStream.writeObject(item);
      dataStream.close();
      return Base64Coder.encodeLines(outputStream.toByteArray());
    } catch (Exception e) { return ""; }
  }

  private List<ItemStack> itemsFromBase64(String data) {
    try {
      ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
      BukkitObjectInputStream dataStream = new BukkitObjectInputStream(inputStream);
      int size = dataStream.readInt();
      List<ItemStack> items = new ArrayList<>();
      for (int i = 0; i < size; i++) items.add((ItemStack) dataStream.readObject());
      dataStream.close();
      return items;
    } catch (Exception e) { return new ArrayList<>(); }
  }
}
