package com.cappleapple.instancednotinfinite.instance;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.config.ServerConfig;
import com.cappleapple.instancednotinfinite.content.ManifestationPortalBlockEntity;
import com.cappleapple.instancednotinfinite.player.PlayerReturnSavedData;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder(InstancedNotInfinite.MOD_ID)
public final class CommandEntryGameTests {
    private static final String FIXTURE = "instancednotinfinite:surface_igloo";

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        List<TestFunction> tests = new ArrayList<>();
        for (boolean random : List.of(false, true)) {
            for (boolean lifecycle : List.of(false, true)) {
                String name = "queued_entry_" + (random ? "random" : "named") + (lifecycle ? "_lifecycle" : "");
                tests.add(test(name, helper -> entersAfterGeneration(helper, random ? null : FIXTURE, lifecycle)));
            }
        }
        for (String reason : List.of("disconnect", "dimension_change", "death", "delete")) {
            tests.add(test("queued_entry_cancels_on_" + reason, helper -> cancelsBeforeTeleport(helper, reason)));
        }
        if (ModList.get().isLoaded("skyarena")) {
            for (String arena : List.of("ice_arena", "sky_arena")) {
                tests.add(new TestFunction("command_arena", "instancednotinfinite.queued_entry_" + arena,
                    "instancednotinfinite_integration:empty", 2400, 0L, true,
                    helper -> entersRealArena(helper, "skyarena:" + arena)));
            }
        }
        return tests;
    }

    private static TestFunction test(String name, java.util.function.Consumer<GameTestHelper> body) {
        return new TestFunction("command_entry", "instancednotinfinite." + name, "minecraft:bastion/mobs/empty", 1200, 0L, true, body);
    }

    private static void entersRealArena(GameTestHelper helper, String dungeon) {
        List<String> previous = List.copyOf(ServerConfig.INSTANCE.structures.get());
        DungeonInstanceManager manager = DungeonInstanceManager.get(helper.getLevel().getServer());
        try {
            ServerConfig.INSTANCE.structures.set(List.of(dungeon));
            manager.rebuildCatalogue();
            entersAfterGeneration(helper, dungeon, false);
        } finally {
            ServerConfig.INSTANCE.structures.set(previous);
            manager.rebuildCatalogue();
        }
    }

    private static void entersAfterGeneration(GameTestHelper helper, String dungeon, boolean lifecycle) {
        var server = helper.getLevel().getServer();
        DungeonInstanceManager manager = DungeonInstanceManager.get(server);
        ServerPlayer player = player(helper);
        ServerLevel original = player.serverLevel();
        String command = "dungeon enter" + (dungeon == null ? "" : " " + dungeon) + (lifecycle ? " lifecycle 123 456 -1" : "");
        long started = System.nanoTime();
        DungeonInstance instance = executeAndFind(helper, player, command);
        InstancedNotInfinite.LOGGER.info("Command queued {} in {} ms with state {}", instance.definition().id(),
            (System.nanoTime() - started) / 1_000_000L, instance.state());
        long queuedAt = server.getTickCount();
        helper.assertTrue(player.serverLevel() == original, "Command teleported before returning to the server tick loop");
        helper.assertValueEqual(instance.state(), InstanceState.CREATING, "Command synchronously finished generation");
        helper.assertTrue(PlayerReturnSavedData.get(server).get(player.getUUID()).isEmpty(), "Return location was captured before generation completed");
        if (dungeon != null) helper.assertValueEqual(instance.definition().id(), dungeon, "Named command selected a different dungeon");
        if (lifecycle) helper.assertValueEqual(instance.lifecycleSettings(), new InstanceLifecycleSettings(123, 456, -1), "Queued command lost lifecycle overrides");
        int count = manager.instances().size();
        helper.assertValueEqual(execute(player, command), 0, "Duplicate command queued a second pending entry for one player");
        helper.assertValueEqual(manager.instances().size(), count, "Duplicate command allocated another instance");
        execute(player, "time query gametime");
        helper.assertValueEqual(instance.state(), InstanceState.CREATING, "An unrelated command completed the queued generation inline");

        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(player.serverLevel().dimension().location().equals(instance.dimensionId()), "Waiting for queued command entry"))
            .thenExecute(() -> {
                helper.assertTrue(server.getTickCount() > queuedAt, "Generation did not yield across server ticks");
                helper.assertValueEqual(instance.state(), InstanceState.ACTIVE, "Player entered an unfinished instance");
                var plan = instance.plan().orElseThrow();
                var pos = DestinationPortalPlacement.position(plan, ServerConfig.INSTANCE.destinationPortalBehindEntryBlocks.get());
                helper.assertTrue(player.serverLevel().getBlockEntity(pos) instanceof ManifestationPortalBlockEntity, "Command entry has no return portal");
                var portal = (ManifestationPortalBlockEntity)player.serverLevel().getBlockEntity(pos);
                helper.assertValueEqual(portal.instanceId().orElseThrow(), instance.id().value(), "Command return portal has the wrong instance binding");
                helper.assertValueEqual(PlayerReturnSavedData.get(server).get(player.getUUID()).orElseThrow().dimension(),
                    original.dimension().location(), "Queued entry lost the original return dimension");
                InstancedNotInfinite.LOGGER.info("Command entry completed {} after {} server ticks with a bound return portal",
                    instance.definition().id(), server.getTickCount() - queuedAt);
                helper.assertTrue(manager.leave(player), "Could not return from the command-created dungeon");
                cleanupPlayer(player);
                delete(manager, instance);
            })
            .thenWaitUntil(() -> helper.assertTrue(manager.get(instance.id()).isEmpty(), "Waiting for command-created instance cleanup"))
            .thenSucceed();
    }

    private static void cancelsBeforeTeleport(GameTestHelper helper, String reason) {
        var server = helper.getLevel().getServer();
        DungeonInstanceManager manager = DungeonInstanceManager.get(server);
        ServerPlayer player = player(helper);
        DungeonInstance instance = executeAndFind(helper, player, "dungeon enter " + FIXTURE);
        helper.assertValueEqual(instance.state(), InstanceState.CREATING, "Cancellation fixture generated synchronously");
        switch (reason) {
            case "disconnect" -> cleanupPlayer(player);
            case "dimension_change" -> player.teleportTo(server.getLevel(Level.NETHER), 0.5, 100, 0.5, 0, 0);
            case "death" -> player.setHealth(0);
            case "delete" -> delete(manager, instance);
            default -> throw new IllegalArgumentException(reason);
        }
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(manager.get(instance.id()).isEmpty(), "Waiting for cancelled entry cleanup: " + reason))
            .thenExecute(() -> {
                helper.assertFalse(player.level().dimension().location().equals(instance.dimensionId()), "Cancelled request teleported the player");
                helper.assertTrue(PlayerReturnSavedData.get(server).get(player.getUUID()).isEmpty(), "Cancelled entry left a phantom return record");
                cleanupPlayer(player);
            })
            .thenSucceed();
    }

    private static DungeonInstance executeAndFind(GameTestHelper helper, ServerPlayer player, String command) {
        DungeonInstanceManager manager = DungeonInstanceManager.get(player.getServer());
        Set<InstanceId> before = manager.instances().stream().map(DungeonInstance::id).collect(Collectors.toSet());
        helper.assertValueEqual(execute(player, command), 1, "Entry command did not accept the request");
        List<DungeonInstance> added = manager.instances().stream().filter(instance -> !before.contains(instance.id())).toList();
        helper.assertValueEqual(added.size(), 1, "Command did not queue exactly one instance");
        return added.getFirst();
    }

    private static int execute(ServerPlayer player, String command) {
        try {
            CommandSourceStack source = player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
            return player.getServer().getCommands().getDispatcher().execute(command, source);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            throw new IllegalStateException("Could not dispatch fixture command: " + command, exception);
        }
    }

    private static ServerPlayer player(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "ini-command-test"), false);
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setInvulnerable(true);
        player.setNoGravity(true);
        return player;
    }

    private static void cleanupPlayer(ServerPlayer player) {
        if (player.getServer().getPlayerList().getPlayer(player.getUUID()) == player) player.getServer().getPlayerList().remove(player);
    }

    private static void delete(DungeonInstanceManager manager, DungeonInstance instance) {
        try {
            manager.delete(instance.id());
        } catch (InstanceOperationException exception) {
            throw new IllegalStateException("Could not clean up command entry fixture", exception);
        }
    }
}
