package com.cappleapple.instancednotinfinite.command;

import com.cappleapple.instancednotinfinite.definition.DungeonDefinitionRegistry;
import com.cappleapple.instancednotinfinite.definition.AutomaticDungeonMetadata;
import com.cappleapple.instancednotinfinite.instance.DungeonInstance;
import com.cappleapple.instancednotinfinite.instance.DungeonInstanceManager;
import com.cappleapple.instancednotinfinite.instance.InstanceId;
import com.cappleapple.instancednotinfinite.instance.InstanceOperationException;
import com.cappleapple.instancednotinfinite.instance.InstanceLifecycleOverrides;
import com.cappleapple.instancednotinfinite.instance.InstanceLifecycleSettings;
import com.cappleapple.instancednotinfinite.api.DungeonManifestationApi;
import com.cappleapple.instancednotinfinite.api.ManifestationView;
import com.cappleapple.instancednotinfinite.manifestation.AnimationMode;
import com.cappleapple.instancednotinfinite.manifestation.DungeonManifestation;
import com.cappleapple.instancednotinfinite.manifestation.DungeonManifestationManager;
import com.cappleapple.instancednotinfinite.manifestation.DungeonTarget;
import com.cappleapple.instancednotinfinite.manifestation.ManifestationOptions;
import com.cappleapple.instancednotinfinite.recipe.PortalRecipeGenerationService;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.Collection;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class DungeonCommands {
    private DungeonCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dungeon")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("list")
                .requires(source -> source.hasPermission(2))
                .executes(DungeonCommands::list))
            .then(Commands.literal("create")
                .requires(source -> source.hasPermission(2))
                .executes(DungeonCommands::createRandom)
                .then(lifecycle(DungeonCommands::createRandomWithLifecycle))
                .then(Commands.argument("dungeon", ResourceLocationArgument.id())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(DungeonDefinitionRegistry.INSTANCE.ids(), builder))
                    .executes(DungeonCommands::create)
                    .then(lifecycle(DungeonCommands::createWithLifecycle))))
            .then(Commands.literal("spawn")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("dungeon", ResourceLocationArgument.id())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(DungeonDefinitionRegistry.INSTANCE.ids(), builder))
                    .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(context -> spawn(context, DungeonTarget.dungeon(
                            ResourceLocationArgument.getId(context, "dungeon")), defaultOrientation(context)))
                        .then(lifecycle(context -> spawn(context, DungeonTarget.dungeon(
                            ResourceLocationArgument.getId(context, "dungeon")), defaultOrientation(context), lifecycle(context))))
                        .then(Commands.argument("orientation", StringArgumentType.word())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                new String[]{"north", "south", "east", "west"}, builder))
                            .executes(context -> spawnWithOrientation(context, DungeonTarget.dungeon(
                                ResourceLocationArgument.getId(context, "dungeon"))))
                            .then(lifecycle(context -> spawnWithOrientation(
                                context, DungeonTarget.dungeon(ResourceLocationArgument.getId(context, "dungeon")), lifecycle(context))))))))
            .then(Commands.literal("spawn-pool")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(context -> spawn(context, DungeonTarget.configuredPool(), defaultOrientation(context)))
                    .then(lifecycle(context -> spawn(
                        context, DungeonTarget.configuredPool(), defaultOrientation(context), lifecycle(context))))
                    .then(Commands.argument("orientation", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                            new String[]{"north", "south", "east", "west"}, builder))
                        .executes(context -> spawnWithOrientation(context, DungeonTarget.configuredPool()))
                        .then(lifecycle(context -> spawnWithOrientation(
                            context, DungeonTarget.configuredPool(), lifecycle(context)))))))
            .then(Commands.literal("enter")
                .requires(source -> source.hasPermission(2))
                .executes(DungeonCommands::createRandomAndEnter)
                .then(lifecycle(DungeonCommands::createRandomAndEnterWithLifecycle))
                .then(Commands.argument("dungeon", ResourceLocationArgument.id())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(DungeonDefinitionRegistry.INSTANCE.ids(), builder))
                    .executes(DungeonCommands::createAndEnter)
                    .then(lifecycle(DungeonCommands::createAndEnterWithLifecycle))))
            .then(Commands.literal("join")
                .then(Commands.argument("instance", UuidArgument.uuid())
                    .suggests(DungeonCommands::suggestInstances)
                    .executes(DungeonCommands::join)))
            .then(Commands.literal("leave").executes(DungeonCommands::leave))
            .then(Commands.literal("complete")
                .executes(DungeonCommands::completeCurrent)
                .then(Commands.argument("instance", UuidArgument.uuid())
                    .requires(source -> source.hasPermission(2))
                    .suggests(DungeonCommands::suggestInstances)
                    .executes(DungeonCommands::completeById)))
            .then(Commands.literal("info")
                .executes(DungeonCommands::infoCurrent)
                .then(Commands.argument("instance", UuidArgument.uuid())
                    .suggests(DungeonCommands::suggestInstances)
                    .executes(DungeonCommands::infoById)))
            .then(Commands.literal("inspect")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("dungeon", ResourceLocationArgument.id())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(DungeonDefinitionRegistry.INSTANCE.ids(), builder))
                    .executes(DungeonCommands::inspect)))
            .then(Commands.literal("delete")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("instance", UuidArgument.uuid())
                    .suggests(DungeonCommands::suggestInstances)
                    .executes(DungeonCommands::delete)))
            .then(Commands.literal("cleanup")
                .requires(source -> source.hasPermission(2))
                .executes(DungeonCommands::cleanup))
            .then(Commands.literal("reload")
                .requires(source -> source.hasPermission(2))
                .executes(DungeonCommands::reload))
            .then(Commands.literal("recipe")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("explain")
                    .then(Commands.argument("dungeon", ResourceLocationArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                            DungeonDefinitionRegistry.INSTANCE.ids(), builder))
                        .executes(DungeonCommands::explainRecipe))))
            .then(Commands.literal("manifestation")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list").executes(DungeonCommands::listManifestations))
                .then(Commands.literal("info")
                    .then(Commands.argument("manifestation", UuidArgument.uuid()).executes(DungeonCommands::manifestationInfo)))
                .then(Commands.literal("cancel")
                    .then(Commands.argument("manifestation", UuidArgument.uuid()).executes(DungeonCommands::cancelManifestation)))
                .then(Commands.literal("finish")
                    .then(Commands.argument("manifestation", UuidArgument.uuid()).executes(DungeonCommands::finishManifestation)))
                .then(Commands.literal("test")
                    .then(Commands.argument("dungeon", ResourceLocationArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(DungeonDefinitionRegistry.INSTANCE.ids(), builder))
                        .then(Commands.argument("mode", StringArgumentType.word())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                java.util.Arrays.stream(AnimationMode.values()).map(Enum::name).toList(), builder))
                            .executes(DungeonCommands::testManifestation)
                            .then(lifecycle(DungeonCommands::testManifestationWithLifecycle)))))));
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        DungeonInstanceManager manager = manager(context);
        Collection<DungeonInstance> instances = manager.instances();
        context.getSource().sendSuccess(() -> Component.literal(
            "Dungeon options: " + DungeonDefinitionRegistry.INSTANCE.size()
                + " (automatic=" + DungeonDefinitionRegistry.INSTANCE.automaticSize()
                + ", datapack=" + DungeonDefinitionRegistry.INSTANCE.legacySize() + ")"
                + "; instances: " + instances.size()), false);
        for (DungeonInstance instance : instances) {
            context.getSource().sendSuccess(() -> describe(instance), false);
        }
        return instances.size();
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestInstances(
        CommandContext<CommandSourceStack> context,
        com.mojang.brigadier.suggestion.SuggestionsBuilder builder
    ) {
        return SharedSuggestionProvider.suggest(
            manager(context).instances().stream().map(instance -> instance.id().toString()).toList(), builder);
    }

    private static int create(CommandContext<CommandSourceStack> context) {
        ResourceLocation dungeon = ResourceLocationArgument.getId(context, "dungeon");
        try {
            DungeonInstance instance = manager(context).create(dungeon);
            context.getSource().sendSuccess(() -> Component.literal(
                "Created " + dungeon + " as " + instance.id() + " in " + instance.dimensionId()), true);
            return Command.SINGLE_SUCCESS;
        } catch (InstanceOperationException exception) {
            return fail(context, exception);
        }
    }

    private static int createWithLifecycle(CommandContext<CommandSourceStack> context) {
        ResourceLocation dungeon = ResourceLocationArgument.getId(context, "dungeon");
        try {
            DungeonInstance instance = manager(context).create(dungeon, lifecycle(context));
            context.getSource().sendSuccess(() -> Component.literal(
                "Created " + dungeon + " as " + instance.id() + " in " + instance.dimensionId()), true);
            return Command.SINGLE_SUCCESS;
        } catch (InstanceOperationException exception) {
            return fail(context, exception);
        }
    }

    private static int createRandom(CommandContext<CommandSourceStack> context) {
        try {
            DungeonInstance instance = manager(context).createRandom();
            context.getSource().sendSuccess(() -> Component.literal(
                "Selected " + instance.definition().id() + " and created instance " + instance.id()
                    + " in " + instance.dimensionId()), true);
            return Command.SINGLE_SUCCESS;
        } catch (InstanceOperationException exception) {
            return fail(context, exception);
        }
    }

    private static int createRandomWithLifecycle(CommandContext<CommandSourceStack> context) {
        try {
            DungeonInstance instance = manager(context).createRandom(lifecycle(context));
            context.getSource().sendSuccess(() -> Component.literal(
                "Selected " + instance.definition().id() + " and created instance " + instance.id()
                    + " in " + instance.dimensionId()), true);
            return Command.SINGLE_SUCCESS;
        } catch (InstanceOperationException exception) {
            return fail(context, exception);
        }
    }

    private static int createAndEnter(CommandContext<CommandSourceStack> context) {
        return queueEntry(context, java.util.Optional.of(ResourceLocationArgument.getId(context, "dungeon")), InstanceLifecycleOverrides.empty());
    }

    private static int createAndEnterWithLifecycle(CommandContext<CommandSourceStack> context) {
        return queueEntry(context, java.util.Optional.of(ResourceLocationArgument.getId(context, "dungeon")), lifecycle(context));
    }

    private static int createRandomAndEnter(CommandContext<CommandSourceStack> context) {
        return queueEntry(context, java.util.Optional.empty(), InstanceLifecycleOverrides.empty());
    }

    private static int createRandomAndEnterWithLifecycle(CommandContext<CommandSourceStack> context) {
        return queueEntry(context, java.util.Optional.empty(), lifecycle(context));
    }

    private static int queueEntry(CommandContext<CommandSourceStack> context, java.util.Optional<ResourceLocation> dungeon,
        InstanceLifecycleOverrides overrides) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            DungeonInstance instance = manager(context).queueEntry(player, dungeon, overrides);
            context.getSource().sendSuccess(() -> Component.literal(
                "Generating " + instance.definition().id() + " as instance " + instance.id() + "; you will enter when it is ready."), false);
            return Command.SINGLE_SUCCESS;
        } catch (Exception exception) {
            return fail(context, exception);
        }
    }

    private static int join(CommandContext<CommandSourceStack> context) {
        try {
            InstanceId id = new InstanceId(UuidArgument.getUuid(context, "instance"));
            manager(context).enter(context.getSource().getPlayerOrException(), id);
            context.getSource().sendSuccess(() -> Component.literal("Entered dungeon instance " + id), false);
            return Command.SINGLE_SUCCESS;
        } catch (Exception exception) {
            return fail(context, exception);
        }
    }

    private static int leave(CommandContext<CommandSourceStack> context) {
        try {
            if (!manager(context).leave(context.getSource().getPlayerOrException())) {
                context.getSource().sendFailure(Component.literal("You are not inside a dungeon instance"));
                return 0;
            }
            return Command.SINGLE_SUCCESS;
        } catch (Exception exception) {
            return fail(context, exception);
        }
    }

    private static int completeCurrent(CommandContext<CommandSourceStack> context) {
        try {
            DungeonInstance instance = manager(context).getPlayerInstance(context.getSource().getPlayerOrException())
                .orElseThrow(() -> new InstanceOperationException("You are not assigned to a dungeon instance"));
            manager(context).complete(instance.id());
            context.getSource().sendSuccess(() -> Component.literal("Completed dungeon instance " + instance.id()), true);
            return Command.SINGLE_SUCCESS;
        } catch (Exception exception) {
            return fail(context, exception);
        }
    }

    private static int completeById(CommandContext<CommandSourceStack> context) {
        InstanceId id = new InstanceId(UuidArgument.getUuid(context, "instance"));
        try {
            manager(context).complete(id);
            context.getSource().sendSuccess(() -> Component.literal("Completed dungeon instance " + id), true);
            return Command.SINGLE_SUCCESS;
        } catch (InstanceOperationException exception) {
            return fail(context, exception);
        }
    }

    private static int infoCurrent(CommandContext<CommandSourceStack> context) {
        try {
            DungeonInstance instance = manager(context).getPlayerInstance(context.getSource().getPlayerOrException())
                .orElseThrow(() -> new InstanceOperationException("You are not assigned to a dungeon instance"));
            context.getSource().sendSuccess(() -> describe(instance), false);
            return Command.SINGLE_SUCCESS;
        } catch (Exception exception) {
            return fail(context, exception);
        }
    }

    private static int infoById(CommandContext<CommandSourceStack> context) {
        InstanceId id = new InstanceId(UuidArgument.getUuid(context, "instance"));
        DungeonInstance instance = manager(context).get(id).orElse(null);
        if (instance == null) {
            context.getSource().sendFailure(Component.literal("Unknown dungeon instance " + id));
            return 0;
        }
        context.getSource().sendSuccess(() -> describe(instance), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int delete(CommandContext<CommandSourceStack> context) {
        InstanceId id = new InstanceId(UuidArgument.getUuid(context, "instance"));
        try {
            manager(context).delete(id);
            context.getSource().sendSuccess(() -> Component.literal("Queued dungeon instance " + id + " for safe deletion"), true);
            return Command.SINGLE_SUCCESS;
        } catch (InstanceOperationException exception) {
            return fail(context, exception);
        }
    }

    private static int inspect(CommandContext<CommandSourceStack> context) {
        ResourceLocation dungeon = ResourceLocationArgument.getId(context, "dungeon");
        AutomaticDungeonMetadata metadata = DungeonDefinitionRegistry.INSTANCE.inspect(dungeon).orElse(null);
        if (metadata == null) {
            if (DungeonDefinitionRegistry.INSTANCE.get(dungeon).isPresent()) {
                context.getSource().sendSuccess(() -> Component.literal(
                    "Dungeon " + dungeon + " is an advanced datapack definition; automatic inference metadata does not apply"), false);
                return Command.SINGLE_SUCCESS;
            }
            context.getSource().sendFailure(Component.literal("Unknown dungeon option " + dungeon));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Structure: " + metadata.structureId()), false);
        context.getSource().sendSuccess(() -> Component.literal("Source: " + String.join(", ", metadata.sources())), false);
        context.getSource().sendSuccess(() -> Component.literal("Resolved biomes: " + metadata.resolvedBiomeCount()), false);
        context.getSource().sendSuccess(() -> Component.literal(
            "Environment: " + metadata.environment() + " (" + metadata.environmentSource() + "; " + metadata.environmentReason() + ")"), false);
        context.getSource().sendSuccess(() -> Component.literal(
            "Padding: horizontal=" + metadata.horizontalPadding() + " vertical=" + metadata.verticalPadding()
                + " weight=" + metadata.weight()), false);
        context.getSource().sendSuccess(() -> Component.literal(
            "Structure type=" + metadata.structureType() + " adaptation=" + metadata.terrainAdaptation()
                + " step=" + metadata.generationStep() + " placement=" + metadata.placement()
                + " variableSize=" + metadata.variableSize()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int cleanup(CommandContext<CommandSourceStack> context) {
        manager(context).requestCleanupRetries();
        context.getSource().sendSuccess(() -> Component.literal("Retried eligible pending dungeon cleanups"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int explainRecipe(CommandContext<CommandSourceStack> context) {
        ResourceLocation dungeon = ResourceLocationArgument.getId(context, "dungeon");
        var report = PortalRecipeGenerationService.INSTANCE.report(dungeon).orElse(null);
        if (report == null) {
            context.getSource().sendFailure(Component.literal("No recipe analysis is cached for dungeon " + dungeon));
            return 0;
        }
        report.explanationLines().forEach(line ->
            context.getSource().sendSuccess(() -> Component.literal(line), false));
        return Command.SINGLE_SUCCESS;
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        Collection<String> selected = context.getSource().getServer().getPackRepository().getSelectedIds();
        context.getSource().getServer().reloadResources(selected).whenComplete((unused, failure) -> {
            if (failure == null) {
                context.getSource().getServer().execute(() -> {
                    manager(context).rebuildCatalogue();
                    context.getSource().sendSuccess(() -> Component.literal(
                        "Rebuilt automatic and datapack dungeon catalogue; active instances retained their snapshots"), true);
                });
            } else {
                context.getSource().sendFailure(Component.literal("Dungeon definition reload failed: " + failure.getMessage()));
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    private static int spawn(CommandContext<CommandSourceStack> context, DungeonTarget target, Direction orientation) {
        return spawn(context, target, orientation, InstanceLifecycleOverrides.empty());
    }

    private static int spawn(
        CommandContext<CommandSourceStack> context,
        DungeonTarget target,
        Direction orientation,
        InstanceLifecycleOverrides lifecycleOverrides
    ) {
        try {
            BlockPos origin = BlockPosArgument.getLoadedBlockPos(context, "pos");
            ServerPlayer initiator = context.getSource().getEntity() instanceof ServerPlayer player ? player : null;
            ManifestationView view = DungeonManifestationApi.spawn(
                context.getSource().getLevel(), origin, target,
                new ManifestationOptions(orientation, AnimationMode.RANDOM_MODE, lifecycleOverrides), initiator);
            context.getSource().sendSuccess(() -> Component.literal(
                "Started manifestation " + view.id() + " for " + view.dungeonId()
                    + " (instance " + view.instanceId() + ") at " + origin.toShortString()), true);
            return Command.SINGLE_SUCCESS;
        } catch (Exception exception) {
            return fail(context, exception);
        }
    }

    private static int spawnWithOrientation(CommandContext<CommandSourceStack> context, DungeonTarget target) {
        return spawnWithOrientation(context, target, InstanceLifecycleOverrides.empty());
    }

    private static int spawnWithOrientation(
        CommandContext<CommandSourceStack> context,
        DungeonTarget target,
        InstanceLifecycleOverrides lifecycleOverrides
    ) {
        try {
            return spawn(context, target, orientation(context), lifecycleOverrides);
        } catch (Exception exception) {
            return fail(context, exception);
        }
    }

    private static int listManifestations(CommandContext<CommandSourceStack> context) {
        Collection<DungeonManifestation> values = DungeonManifestationManager.get(context.getSource().getServer()).values();
        context.getSource().sendSuccess(() -> Component.literal("Manifestations: " + values.size()), false);
        values.forEach(value -> context.getSource().sendSuccess(() -> describe(value), false));
        return values.size();
    }

    private static int manifestationInfo(CommandContext<CommandSourceStack> context) {
        UUID id = UuidArgument.getUuid(context, "manifestation");
        DungeonManifestation value = DungeonManifestationManager.get(context.getSource().getServer()).get(id).orElse(null);
        if (value == null) {
            context.getSource().sendFailure(Component.literal("Unknown dungeon manifestation " + id));
            return 0;
        }
        context.getSource().sendSuccess(() -> describe(value), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int cancelManifestation(CommandContext<CommandSourceStack> context) {
        UUID id = UuidArgument.getUuid(context, "manifestation");
        try {
            DungeonManifestationManager.get(context.getSource().getServer()).cancel(id, "Cancelled by command");
            context.getSource().sendSuccess(() -> Component.literal("Cancelled manifestation " + id), true);
            return Command.SINGLE_SUCCESS;
        } catch (Exception exception) {
            return fail(context, exception);
        }
    }

    private static int finishManifestation(CommandContext<CommandSourceStack> context) {
        UUID id = UuidArgument.getUuid(context, "manifestation");
        try {
            DungeonManifestationManager.get(context.getSource().getServer()).finishAnimation(id);
            context.getSource().sendSuccess(() -> Component.literal(
                "Forced visual completion for manifestation " + id + "; portal remains generation-gated"), true);
            return Command.SINGLE_SUCCESS;
        } catch (Exception exception) {
            return fail(context, exception);
        }
    }

    private static int testManifestation(CommandContext<CommandSourceStack> context) {
        return testManifestation(context, InstanceLifecycleOverrides.empty());
    }

    private static int testManifestationWithLifecycle(CommandContext<CommandSourceStack> context) {
        return testManifestation(context, lifecycle(context));
    }

    private static int testManifestation(
        CommandContext<CommandSourceStack> context,
        InstanceLifecycleOverrides lifecycleOverrides
    ) {
        try {
            ResourceLocation dungeon = ResourceLocationArgument.getId(context, "dungeon");
            AnimationMode mode = AnimationMode.parse(StringArgumentType.getString(context, "mode"));
            BlockPos origin = BlockPos.containing(context.getSource().getPosition());
            Direction orientation = defaultOrientation(context);
            ServerPlayer initiator = context.getSource().getEntity() instanceof ServerPlayer player ? player : null;
            ManifestationView view = DungeonManifestationApi.spawn(
                context.getSource().getLevel(), origin, DungeonTarget.dungeon(dungeon),
                new ManifestationOptions(orientation, mode, lifecycleOverrides), initiator);
            context.getSource().sendSuccess(() -> Component.literal(
                "Started test manifestation " + view.id() + " using " + view.animationMode()), true);
            return Command.SINGLE_SUCCESS;
        } catch (Exception exception) {
            return fail(context, exception);
        }
    }

    private static Direction orientation(CommandContext<CommandSourceStack> context) throws InstanceOperationException {
        Direction direction = Direction.byName(StringArgumentType.getString(context, "orientation"));
        if (direction == null || !direction.getAxis().isHorizontal()) {
            throw new InstanceOperationException("Portal orientation must be north, south, east, or west");
        }
        return direction;
    }

    private static Direction defaultOrientation(CommandContext<CommandSourceStack> context) {
        return Direction.fromYRot(context.getSource().getRotation().y);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> lifecycle(Command<CommandSourceStack> command) {
        return Commands.literal("lifecycle")
            .then(Commands.argument("openSeconds", IntegerArgumentType.integer(
                    InstanceLifecycleSettings.INFINITE, InstanceLifecycleSettings.MAX_SECONDS))
                .then(Commands.argument("postVisitSeconds", IntegerArgumentType.integer(
                        InstanceLifecycleSettings.INFINITE, InstanceLifecycleSettings.MAX_SECONDS))
                    .then(Commands.argument("forceCollapseSeconds", IntegerArgumentType.integer(
                            InstanceLifecycleSettings.INFINITE, InstanceLifecycleSettings.MAX_SECONDS))
                        .executes(command))));
    }

    private static InstanceLifecycleOverrides lifecycle(CommandContext<CommandSourceStack> context) {
        return InstanceLifecycleOverrides.of(
            IntegerArgumentType.getInteger(context, "openSeconds"),
            IntegerArgumentType.getInteger(context, "postVisitSeconds"),
            IntegerArgumentType.getInteger(context, "forceCollapseSeconds"));
    }

    private static DungeonInstanceManager manager(CommandContext<CommandSourceStack> context) {
        return DungeonInstanceManager.get(context.getSource().getServer());
    }

    private static Component describe(DungeonInstance instance) {
        return Component.literal(
            instance.id().shortId() + " " + instance.definition().id() + " " + instance.state()
                + " biome=" + instance.biomeId() + " players=" + instance.assignedPlayers().size()
                + " lifecycle=" + instance.lifecycleSettings().openSeconds() + "/"
                    + instance.lifecycleSettings().postVisitSeconds() + "/"
                    + instance.lifecycleSettings().forceCollapseSeconds()
                + " dimension=" + instance.dimensionId()
                + instance.failureReason().map(reason -> " failure=" + reason).orElse(""));
    }

    private static Component describe(DungeonManifestation value) {
        return Component.literal(
            value.id().toString().substring(0, 8) + " " + value.dungeonId() + " " + value.state()
                + " generation=" + Math.round(value.generationProgress() * 100.0) + "%"
                + " animation=" + Math.round(value.animationProgress() * 100.0) + "%"
                + " mode=" + value.animationMode() + " origin=" + value.originDimension() + " " + value.origin().toShortString()
                + " instance=" + value.instanceId()
                + value.failureReason().map(reason -> " failure=" + reason).orElse(""));
    }

    private static int fail(CommandContext<CommandSourceStack> context, Exception exception) {
        context.getSource().sendFailure(Component.literal(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));
        return 0;
    }
}
