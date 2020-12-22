/*
 * Created by David Luedtke (MrKinau)
 * 2019/10/18
 */

package systems.kinau.fishingbot.bot;

import lombok.Getter;
import lombok.Setter;
import systems.kinau.fishingbot.FishingBot;
import systems.kinau.fishingbot.event.EventHandler;
import systems.kinau.fishingbot.event.Listener;
import systems.kinau.fishingbot.event.custom.RespawnEvent;
import systems.kinau.fishingbot.event.play.*;
import systems.kinau.fishingbot.fishing.AnnounceType;
import systems.kinau.fishingbot.fishing.EjectionRule;
import systems.kinau.fishingbot.network.protocol.ProtocolConstants;
import systems.kinau.fishingbot.network.protocol.play.*;
import systems.kinau.fishingbot.network.protocol.play.PacketOutEntityAction.EntityAction;
import systems.kinau.fishingbot.network.utils.ItemUtils;
import systems.kinau.fishingbot.network.utils.LocationUtils;
import systems.kinau.fishingbot.network.utils.StringUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class Player implements Listener {

    @Getter @Setter private double x;
    @Getter @Setter private double y;
    @Getter @Setter private double z;
    @Getter @Setter private float yaw;
    @Getter @Setter private float pitch;
    @Getter @Setter private float originYaw = -255;
    @Getter @Setter private float originPitch = -255;

    @Getter @Setter private int experience;
    @Getter @Setter private int levels;
    @Getter @Setter private float health = -1;
    @Getter @Setter private boolean sentLowHealth;
    @Getter @Setter private boolean respawning;
    @Getter @Setter private boolean sneaking;

    @Getter @Setter private int heldSlot;
    @Getter @Setter private Slot heldItem;
    @Getter @Setter private Inventory inventory;
    @Getter         private Map<Integer, Inventory> openedInventories;

    @Getter @Setter private UUID uuid;

    @Getter @Setter private int entityID = -1;
    @Getter @Setter private int lastPing = 500;

    @Getter @Setter private Thread lookThread;
    @Getter         private final List<LookEjectFunction> lookEjectFunctionQueue = new ArrayList<>();

    public Player() {
        this.openedInventories = new HashMap<>();
        this.inventory = new Inventory();
        FishingBot.getInstance().getCurrentBot().getEventManager().registerListener(this);
    }

    @EventHandler
    public void onPosLookChange(PosLookChangeEvent event) {
        this.x = event.getX();
        this.y = event.getY();
        this.z = event.getZ();
        this.yaw = event.getYaw();
        this.pitch = event.getPitch();
        if (originYaw == -255 && originPitch == -255) {
            this.originYaw = yaw;
            this.originPitch = pitch;
        }
        if (FishingBot.getInstance().getCurrentBot().getServerProtocol() >= ProtocolConstants.MINECRAFT_1_9)
            FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutTeleportConfirm(event.getTeleportId()));

    }

    @EventHandler
    public void onUpdateXP(UpdateExperienceEvent event) {
        if (getLevels() >= 0 && getLevels() < event.getLevel()) {
            if (FishingBot.getInstance().getCurrentBot().getConfig().getAnnounceTypeConsole() != AnnounceType.NONE)
                FishingBot.getI18n().info("announce-level-up", String.valueOf(event.getLevel()));
            if (FishingBot.getInstance().getCurrentBot().getConfig().isAnnounceLvlUp() && !FishingBot.getInstance().getCurrentBot().getConfig().getAnnounceLvlUpText().equalsIgnoreCase("false"))
                FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutChat(FishingBot.getInstance().getCurrentBot().getConfig().getAnnounceLvlUpText().replace("%lvl%", String.valueOf(event.getLevel()))));
        }

        this.levels = event.getLevel();
        this.experience = event.getExperience();
    }

    @EventHandler
    public void onSetHeldItem(SetHeldItemEvent event) {
        this.heldSlot = event.getSlot();
    }

    @EventHandler
    public void onUpdateSlot(UpdateSlotEvent event) {
        if (event.getWindowId() != 0)
            return;

        Slot slot = event.getSlot();

        if (getInventory() != null)
            getInventory().setItem(event.getSlotId(), slot);

        if (event.getSlotId() == getHeldSlot())
            this.heldItem = slot;
        if (!(event.getSlotId() == getHeldSlot() && ItemUtils.isFishingRod(slot)))
            executeEjectionRules(FishingBot.getInstance().getCurrentBot().getConfig().getAutoLootEjectionRules(), slot, event.getSlotId());
    }

    @EventHandler
    public void onUpdateWindow(UpdateWindowItemsEvent event) {
        System.out.println("UPDATE WINDOW ITEMS: " + event.getWindowId());
        if (event.getWindowId() == 0) {
            for (int i = 0; i < event.getSlots().size(); i++) {
                getInventory().setItem(i, event.getSlots().get(i));
                if (i == getHeldSlot())
                    this.heldItem = event.getSlots().get(i);
                if (!(i == getHeldSlot() && ItemUtils.isFishingRod(event.getSlots().get(i))))
                    executeEjectionRules(FishingBot.getInstance().getCurrentBot().getConfig().getAutoLootEjectionRules(), event.getSlots().get(i), (short) i);
            }
        } else if (event.getWindowId() > 0) {
            Inventory inventory;
            if (getOpenedInventories().containsKey(event.getWindowId()))
                inventory = getOpenedInventories().get(event.getWindowId());
            else {
                inventory = new Inventory();
                inventory.setWindowId(event.getWindowId());
                getOpenedInventories().put(event.getWindowId(), inventory);
            }
            for (int i = 0; i < event.getSlots().size(); i++)
                inventory.setItem(i, event.getSlots().get(i));
        }
    }

    @EventHandler
    public void onInventoryCloseEvent(InventoryCloseEvent event) {
        System.out.println("CLOSE WINDOW: " + event.getWindowId());
        getOpenedInventories().remove(event.getWindowId());
    }

    @EventHandler
    public void onJoinGame(JoinGameEvent event) {
        setEntityID(event.getEid());
        respawn();
    }

    @EventHandler
    public void onUpdateHealth(UpdateHealthEvent event) {
        if (event.getEid() != getEntityID())
            return;

        if (getHealth() != -1 && event.getHealth() <= 0 && getEntityID() != -1 && !isRespawning()) {
            setRespawning(true);
            FishingBot.getInstance().getCurrentBot().getEventManager().callEvent(new RespawnEvent());
            this.sneaking = false;
            respawn();
        } else if (event.getHealth() > 0 && isRespawning())
            setRespawning(false);

        if (FishingBot.getInstance().getCurrentBot().getConfig().isAutoCommandBeforeDeathEnabled()) {
            if (event.getHealth() < getHealth() && event.getHealth() <= FishingBot.getInstance().getCurrentBot().getConfig().getMinHealthBeforeDeath() && !isSentLowHealth()) {
                for (String command : FishingBot.getInstance().getCurrentBot().getConfig().getAutoCommandBeforeDeath()) {
                    sendMessage(command.replace("%prefix%", FishingBot.PREFIX));
                }
                setSentLowHealth(true);
            } else if (isSentLowHealth() && event.getHealth() > FishingBot.getInstance().getCurrentBot().getConfig().getMinHealthBeforeDeath())
                setSentLowHealth(false);
        }

        if (FishingBot.getInstance().getCurrentBot().getConfig().isAutoQuitBeforeDeathEnabled() && event.getHealth() < getHealth()
                && event.getHealth() <= FishingBot.getInstance().getCurrentBot().getConfig().getMinHealthBeforeQuit() && event.getHealth() != 0.0) {
            FishingBot.getI18n().warning("module-fishing-health-threshold-reached");
            FishingBot.getInstance().getCurrentBot().setPreventReconnect(true);
            FishingBot.getInstance().getCurrentBot().setRunning(false);
        }

        this.health = event.getHealth();
    }

    @EventHandler
    public void onRespawn(RespawnEvent event) {
        new Thread(() -> {
            try {
                Thread.sleep(FishingBot.getInstance().getCurrentBot().getConfig().getAutoCommandOnRespawnDelay());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (FishingBot.getInstance().getCurrentBot().getConfig().isAutoCommandOnRespawnEnabled()) {
                for (String command : FishingBot.getInstance().getCurrentBot().getConfig().getAutoCommandOnRespawn()) {
                    sendMessage(command.replace("%prefix%", FishingBot.PREFIX));
                }
            }
        }).start();
    }

    @EventHandler
    public void onPingUpdate(PingChangeEvent event) {
        setLastPing(event.getPing());
    }

    public void respawn() {
        FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutClientStatus(PacketOutClientStatus.Action.PERFORM_RESPAWN));

        if (FishingBot.getInstance().getCurrentBot().getConfig().isAutoSneak()) {
            FishingBot.getScheduler().schedule(() -> {
                FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutEntityAction(EntityAction.START_SNEAKING));
                this.sneaking = true;
            }, 250, TimeUnit.MILLISECONDS);
        }
    }

    public void sendMessage(String message) {
        for (String line : message.split("\n")) {
            if (FishingBot.getInstance().getCurrentBot().getServerProtocol() == ProtocolConstants.MINECRAFT_1_8) {
                for (String split : StringUtils.splitDescription(line)) {
                    FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutChat(split));
                }
            } else {
                FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutChat(line));
            }
        }
    }

    public void dropStack(short slot, short actionNumber) {
        Slot emptySlot = new Slot(false, -1, (byte) -1, (short) -1, new byte[]{0});

        FishingBot.getInstance().getCurrentBot().getNet().sendPacket(
                new PacketOutClickWindow(
                        /* player inventory */ 0,
                        slot,
                        /* drop entire stack */ (byte) 1,
                        /* action count starting at 1 */ actionNumber,
                        /* drop entire stack */ 4,
                        /* empty slot */ emptySlot
                )
        );

        FishingBot.getInstance().getCurrentBot().getPlayer().getInventory().setItem(slot, emptySlot);
    }

    public void swapToHotBar(int slotId, int hotBarButton) {
        FishingBot.getInstance().getCurrentBot().getNet().sendPacket(
                new PacketOutClickWindow(
                        /* player inventory */ 0,
                        /* the clicked slot */ (short) slotId,
                        /* use hotBar Button */ (byte) hotBarButton,
                        /* action count starting at 1 */ (short) 1,
                        /* hotBar button mode */ 2,
                        /* slot */ getInventory().getContent().get(slotId)
                )
        );
        try { Thread.sleep(20); } catch (InterruptedException e) { e.printStackTrace(); }
        closeInventory();

        Slot slot = FishingBot.getInstance().getCurrentBot().getPlayer().getInventory().getContent().get(slotId);
        FishingBot.getInstance().getCurrentBot().getPlayer().getInventory().getContent().put(slotId, FishingBot.getInstance().getCurrentBot().getPlayer().getInventory().getContent().get(hotBarButton + 36));
        FishingBot.getInstance().getCurrentBot().getPlayer().getInventory().getContent().put(hotBarButton + 36, slot);
    }

    public void shiftToInventory(int slotId, Inventory inventory) {
        for (int i = 0; i < inventory.getContent().size(); i++) {
            Slot slot = inventory.getContent().get(i);
            if (slot.isPresent())
                System.out.println(slot.getItemCount() + "x "+ ItemUtils.getItemName(slot) + "[" + i + "]");
        }
        FishingBot.getInstance().getCurrentBot().getNet().sendPacket(
                new PacketOutClickWindow(
                        /* player inventory */ inventory.getWindowId(),
                        /* the clicked slot */ (short) (slotId + 18),
                        /* use right click */ (byte) 0,
                        /* action count starting at 1 */ inventory.getActionCounter(),
                        /* shift click mode */ 1,
                        /* slot */ getInventory().getContent().get(slotId)
                )
        );
        try { Thread.sleep(20); } catch (InterruptedException e) { e.printStackTrace(); }
    }

    public void look(LocationUtils.Direction direction, Consumer<Boolean> onFinish) {
        look(direction.getYaw(), getPitch(), 8, onFinish, false, (short)-1);
    }

    public void look(float yaw, float pitch, int speed) {
        look(yaw, pitch, speed, null, false, (short)-1);
    }

    public void look(LookEjectFunction lookEjectFunction) {
        look(lookEjectFunction.getYaw(), lookEjectFunction.getPitch(), lookEjectFunction.getSpeed(), lookEjectFunction.getOnFinish(), false, lookEjectFunction.getSlot());
    }

    public void look(float yaw, float pitch, int speed, Consumer<Boolean> onFinish, boolean force, short dropSlot) {
        if (lookThread != null && lookThread.isAlive() && !force) {
            lookEjectFunctionQueue.add(new LookEjectFunction(yaw, pitch, speed, onFinish, dropSlot));
            return;
        } else if (lookThread != null && lookThread.isAlive()) {
            internalLook(yaw, pitch, speed, onFinish);
            return;
        }

        this.lookThread = new Thread(() -> {
            internalLook(yaw, pitch, speed, onFinish);
        });
        getLookThread().start();
    }

    private void internalLook(float yaw, float pitch, int speed, Consumer<Boolean> onFinish) {
        float yawDiff = LocationUtils.yawDiff(getYaw(), yaw);
        float pitchDiff = LocationUtils.yawDiff(getPitch(), pitch);

        int steps = (int) Math.ceil(Math.max(Math.abs(yawDiff), Math.abs(pitchDiff)) / Math.max(1, speed));
        float yawPerStep = yawDiff / steps;
        float pitchPerStep = pitchDiff / steps;

        for (int i = 0; i < steps; i++) {
            setYaw(getYaw() + yawPerStep);
            setPitch(getPitch() + pitchPerStep);
            if (getYaw() > 180)
                setYaw(-180 + (getYaw() - 180));
            if (getYaw() < -180)
                setYaw(180 + (getYaw() + 180));
            FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutPosLook(getX(), getY(), getZ(), getYaw(), getPitch(), true));
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (onFinish != null)
            onFinish.accept(true);

        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        if (!getLookEjectFunctionQueue().isEmpty()) {
            LookEjectFunction lookEjectFunction = getLookEjectFunctionQueue().remove(0);
            look(lookEjectFunction.getYaw(), lookEjectFunction.getPitch(), lookEjectFunction.getSpeed(), lookEjectFunction.getOnFinish(), true, lookEjectFunction.getSlot());
        }
    }

    public void executeEjectionRules(List<EjectionRule> ejectionRules, Slot updatedItem, short slotId) {
        if (!updatedItem.isPresent())
            return;
        String itemName = ItemUtils.getItemName(updatedItem);
        for (EjectionRule ejectionRule : ejectionRules) {
            if (ejectionRule.getAllowList().contains(itemName)) {
                switch (ejectionRule.getEjectionType()) {
                    case DROP: {
                        for (LookEjectFunction lookEjectFunction : getLookEjectFunctionQueue()) {
                            if (lookEjectFunction.getSlot() == slotId)
                                return;
                        }
                        look(ejectionRule.getDirection().getYaw(), getPitch(), FishingBot.getInstance().getCurrentBot().getConfig().getLookSpeed(), finished -> {
                            dropStack(slotId, (short) (slotId - 8));
                            look(getOriginYaw(), getPitch(), FishingBot.getInstance().getCurrentBot().getConfig().getLookSpeed(), finished2 -> {
                                FishingBot.getInstance().getCurrentBot().getFishingModule().finishedLooking();
                            }, true, (short) -1);
                        }, false, slotId);
                        return;
                    }
                    case FILL_CHEST: {
                        openAdjacentChest(ejectionRule.getDirection());
                        new Thread(() -> {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            System.out.println(getOpenedInventories());
                            for (Inventory inventory : getOpenedInventories().values()) {
                                shiftToInventory(slotId, inventory);
                                inventory.setActionCounter((short) (inventory.getActionCounter() + 1));
                            }
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            for (Integer window : new HashSet<>(getOpenedInventories().keySet())) {
                                closeInventory(window);
                            }
                        }).start();
                        return;
                    }
                }
                break;
            }
        }
    }

    public boolean isCurrentlyLooking() {
        return !(lookThread == null || lookThread.isInterrupted() || !lookThread.isAlive());
    }

    public void openAdjacentChest(LocationUtils.Direction direction) {
        int x = (int)Math.floor(getX());
        int y = (int)Math.floor(getY());
        int z = (int)Math.floor(getZ());
        PacketOutBlockPlace.BlockFace blockFace = PacketOutBlockPlace.BlockFace.SOUTH;
        switch (direction) {
            case EAST: x++; blockFace = PacketOutBlockPlace.BlockFace.WEST; break;
            case WEST: x--; blockFace = PacketOutBlockPlace.BlockFace.EAST; break;
            case NORTH: z--; blockFace = PacketOutBlockPlace.BlockFace.SOUTH; break;
            case SOUTH: z++; blockFace = PacketOutBlockPlace.BlockFace.NORTH; break;
        }
        FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutBlockPlace(
                PacketOutBlockPlace.Hand.MAIN_HAND,
                x, y, z, blockFace,
                0.5F, 0.5F, 0.5F,
                false
        ));
    }

    public void closeInventory() {
        closeInventory(0);
    }

    public void closeInventory(int windowId) {
        FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutCloseInventory(windowId));
    }
}
