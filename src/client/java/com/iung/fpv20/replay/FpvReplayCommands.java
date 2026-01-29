package com.iung.fpv20.replay;

import com.iung.fpv20.Fpv20;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import java.nio.file.Path;

public final class FpvReplayCommands {
    private FpvReplayCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("fpvreplay")
                .then(ClientCommandManager.literal("load")
                        .then(ClientCommandManager.argument("path", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String pathArg = StringArgumentType.getString(ctx, "path");
                                    Path path = Path.of(pathArg);
                                    try {
                                        int count = FpvReplayManager.loadFromLog(path);
                                        ctx.getSource().sendFeedback(
                                                Text.literal("fpv replay loaded: " + count + " frames"));
                                    } catch (Exception e) {
                                        ctx.getSource().sendError(
                                                Text.literal("fpv replay load failed: " + e.getMessage()));
                                    }
                                    return 1;
                                })))
                .then(ClientCommandManager.literal("render")
                        .then(ClientCommandManager.argument("path", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String pathArg = StringArgumentType.getString(ctx, "path");
                                    Path path = Path.of(pathArg);
                                    try {
                                        int count = FpvReplayManager.loadFromLog(path);
                                        FpvReplayManager.startReplay();
                                        ctx.getSource().sendFeedback(
                                                Text.literal("fpv replay render: " + count + " frames"));
                                    } catch (Exception e) {
                                        ctx.getSource().sendError(
                                                Text.literal("fpv replay render failed: " + e.getMessage()));
                                    }
                                    return 1;
                                })
                                .then(ClientCommandManager.argument("fps", IntegerArgumentType.integer(1, 1000))
                                        .executes(ctx -> {
                                            String pathArg = StringArgumentType.getString(ctx, "path");
                                            int fps = IntegerArgumentType.getInteger(ctx, "fps");
                                            Path path = Path.of(pathArg);
                                            try {
                                                int count = FpvReplayManager.loadFromLog(path);
                                                FpvReplayManager.setReplayFpsOverride(fps);
                                                FpvReplayManager.startReplay();
                                                ctx.getSource().sendFeedback(
                                                        Text.literal("fpv replay render: " + count + " frames @ " + fps + " fps"));
                                            } catch (Exception e) {
                                                ctx.getSource().sendError(
                                                        Text.literal("fpv replay render failed: " + e.getMessage()));
                                            }
                                            return 1;
                                        }))))
                .then(ClientCommandManager.literal("play")
                        .executes(ctx -> {
                            FpvReplayManager.startReplay();
                            ctx.getSource().sendFeedback(Text.literal("fpv replay started"));
                            return 1;
                        })
                        .then(ClientCommandManager.argument("fps", IntegerArgumentType.integer(1, 1000))
                                .executes(ctx -> {
                                    int fps = IntegerArgumentType.getInteger(ctx, "fps");
                                    FpvReplayManager.setReplayFpsOverride(fps);
                                    FpvReplayManager.startReplay();
                                    ctx.getSource().sendFeedback(Text.literal("fpv replay started @ " + fps + " fps"));
                                    return 1;
                                })))
                .then(ClientCommandManager.literal("record")
                        .executes(ctx -> {
                            if (FpvReplayManager.isRecording()) {
                                FpvReplayManager.stopRecording();
                                ctx.getSource().sendFeedback(Text.literal("fpv replay recording stopped"));
                            } else {
                                FpvReplayManager.stopReplay();
                                FpvReplayManager.startRecording();
                                var dir = FpvReplayManager.getCurrentRecordingDir();
                                var log = FpvReplayManager.getCurrentLogPath();
                                ctx.getSource().sendFeedback(Text.literal(
                                        "fpv replay recording started in " + (dir != null ? dir : "<unknown>")));
                                ctx.getSource().sendFeedback(Text.literal(
                                        "fpv replay log: " + (log != null ? log : "<unknown>")));
                            }
                            return 1;
                        }))
                .then(ClientCommandManager.literal("stop")
                        .executes(ctx -> {
                            FpvReplayManager.stopReplay();
                            ctx.getSource().sendFeedback(Text.literal("fpv replay stopped"));
                            return 1;
                        }))
                .then(ClientCommandManager.literal("clear")
                        .executes(ctx -> {
                            FpvReplayManager.clear();
                            ctx.getSource().sendFeedback(Text.literal("fpv replay cleared"));
                            return 1;
                        }))
                .then(ClientCommandManager.literal("status")
                        .executes(ctx -> {
                            boolean rec = FpvReplayManager.isRecording();
                            boolean rep = FpvReplayManager.isReplaying();
                            int count = FpvReplayManager.getRecordedFrameCount();
                            int fpsOverride = FpvReplayManager.getReplayFpsOverride();
                            float speed = FpvReplayManager.getReplaySpeed();
                            var dir = FpvReplayManager.getCurrentRecordingDir();
                            var log = FpvReplayManager.getCurrentLogPath();
                            ctx.getSource().sendFeedback(Text.literal(
                                    "fpv replay status: recording=" + rec + " replaying=" + rep + " frames=" + count + " fpsOverride=" + fpsOverride + " speed=" + speed));
                            if (dir != null) {
                                ctx.getSource().sendFeedback(Text.literal("fpv replay dir: " + dir));
                            }
                            if (log != null) {
                                ctx.getSource().sendFeedback(Text.literal("fpv replay log: " + log));
                            }
                            return 1;
                        }))
                .then(ClientCommandManager.literal("fps")
                        .then(ClientCommandManager.argument("fps", IntegerArgumentType.integer(1, 1000))
                                .executes(ctx -> {
                                    int fps = IntegerArgumentType.getInteger(ctx, "fps");
                                    FpvReplayManager.setReplayFpsOverride(fps);
                                    ctx.getSource().sendFeedback(Text.literal("fpv replay fps override set to " + fps));
                                    return 1;
                                })))
                .then(ClientCommandManager.literal("speed")
                        .then(ClientCommandManager.argument("speed", FloatArgumentType.floatArg(0.01f, 100.0f))
                                .executes(ctx -> {
                                    float speed = FloatArgumentType.getFloat(ctx, "speed");
                                    FpvReplayManager.setReplaySpeed(speed);
                                    ctx.getSource().sendFeedback(Text.literal("fpv replay speed set to " + speed));
                                    return 1;
                                }))));

        Fpv20.LOGGER.info("Registered fpvreplay client commands");
    }
}
