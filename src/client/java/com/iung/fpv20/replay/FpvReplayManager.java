package com.iung.fpv20.replay;

import com.iung.fpv20.Fpv20;
import com.iung.fpv20.Fpv20Client;
import com.iung.fpv20.flying.GlobalFlying;
import com.iung.fpv20.mixin.client.CameraAccessor;
import com.iung.fpv20.mixin.client.EntityAccessor;
import com.iung.fpv20.physics.PhysicsCore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.joml.Quaternionf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class FpvReplayManager {
    public interface FrameRenderer {
        void renderFrame(ReplayFrame frame);

        FrameRenderer NOOP = frame -> {
        };
    }

    public enum Mode {
        IDLE,
        RECORDING,
        REPLAYING
    }

    private static final Object LOCK = new Object();
    public static final int MIN_REPLAY_FPS = 60;
    private static final Path DEFAULT_REPLAY_ROOT = Path.of("fpv_replays");
    private static final String DEFAULT_LOG_NAME = "camera_log.gz";
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static final List<ReplayFrame> FRAMES = new ArrayList<>();
    private static FrameRenderer frameRenderer = FrameRenderer.NOOP;
    private static FpvReplayLogWriter logWriter;

    private static Mode mode = Mode.IDLE;
    private static ReplayFrame currentFrame;

    private static long recordLastRealNano;
    private static long recordTimeNanos;

    private static long replayStartRealNano;
    private static long replayLastPollNano;
    private static long replayStepNanos = 1_000_000_000L / MIN_REPLAY_FPS;
    private static int playheadIndex;
    private static long currentReplayTimeNanos;
    private static int replayFpsOverride;
    private static float replaySpeed = 1.0f;
    private static Path currentRecordingDir;
    private static Path currentLogPath;

    private FpvReplayManager() {
    }

    public static void setFrameRenderer(FrameRenderer renderer) {
        synchronized (LOCK) {
            frameRenderer = renderer != null ? renderer : FrameRenderer.NOOP;
        }
    }

    public static void startRecording() {
        synchronized (LOCK) {
            FRAMES.clear();
            mode = Mode.RECORDING;
            recordLastRealNano = 0L;
            recordTimeNanos = 0L;
            currentFrame = null;
            try {
                currentRecordingDir = resolveRecordingDir();
                Files.createDirectories(currentRecordingDir);
                currentLogPath = currentRecordingDir.resolve(DEFAULT_LOG_NAME);
                logWriter = FpvReplayLogWriter.open(currentLogPath);
            } catch (IOException e) {
                logWriter = null;
                Fpv20.LOGGER.error("FPV replay: failed to open log file {}", currentLogPath, e);
            }
            Fpv20.LOGGER.info("FPV replay: recording started");
        }
    }

    public static void stopRecording() {
        synchronized (LOCK) {
            if (mode == Mode.RECORDING) {
                mode = Mode.IDLE;
                closeLogWriter();
                Fpv20.LOGGER.info("FPV replay: recording stopped ({} frames)", FRAMES.size());
            }
        }
    }

    public static void startReplay() {
        synchronized (LOCK) {
            if (FRAMES.isEmpty()) {
                Fpv20.LOGGER.warn("FPV replay: no frames to replay");
                return;
            }
            mode = Mode.REPLAYING;
            replayStartRealNano = System.nanoTime();
            replayLastPollNano = replayStartRealNano;
            playheadIndex = 0;
            currentFrame = FRAMES.get(0);
            int targetFps = replayFpsOverride > 0 ? replayFpsOverride : Math.max(MIN_REPLAY_FPS, estimateRecordingFps());
            replayStepNanos = 1_000_000_000L / Math.max(1, targetFps);
            currentReplayTimeNanos = 0L;
            Fpv20.LOGGER.info("FPV replay: replay started ({} frames, {} fps)", FRAMES.size(),
                    1_000_000_000L / replayStepNanos);
        }
    }

    public static void stopReplay() {
        synchronized (LOCK) {
            if (mode == Mode.REPLAYING) {
                mode = Mode.IDLE;
                currentReplayTimeNanos = 0L;
                Fpv20.LOGGER.info("FPV replay: replay stopped");
            }
        }
    }

    public static boolean isRecording() {
        return mode == Mode.RECORDING;
    }

    public static boolean isReplaying() {
        return mode == Mode.REPLAYING;
    }

    public static ReplayFrame getCurrentFrame() {
        synchronized (LOCK) {
            return currentFrame;
        }
    }

    public static long getReplayTimeMillis() {
        synchronized (LOCK) {
            return currentReplayTimeNanos / 1_000_000L;
        }
    }

    public static long getRecordTimeMillis() {
        synchronized (LOCK) {
            return recordTimeNanos / 1_000_000L;
        }
    }

    public static Path getCurrentRecordingDir() {
        synchronized (LOCK) {
            return currentRecordingDir;
        }
    }

    public static Path getCurrentLogPath() {
        synchronized (LOCK) {
            return currentLogPath;
        }
    }

    public static void setReplayFpsOverride(int fps) {
        synchronized (LOCK) {
            replayFpsOverride = Math.max(1, fps);
        }
    }

    public static int getReplayFpsOverride() {
        synchronized (LOCK) {
            return replayFpsOverride;
        }
    }

    public static void setReplaySpeed(float speed) {
        synchronized (LOCK) {
            if (speed <= 0.0f) {
                replaySpeed = 1.0f;
            } else {
                replaySpeed = speed;
            }
        }
    }

    public static float getReplaySpeed() {
        synchronized (LOCK) {
            return replaySpeed;
        }
    }

    public static int getRecordedFrameCount() {
        synchronized (LOCK) {
            return FRAMES.size();
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            FRAMES.clear();
            currentFrame = null;
            closeLogWriter();
            mode = Mode.IDLE;
        }
    }

    public static int loadFromLog(Path path) throws IOException {
        synchronized (LOCK) {
            closeLogWriter();
            mode = Mode.IDLE;
            FRAMES.clear();
            List<ReplayFrame> frames = FpvReplayLogParser.parse(path);
            FRAMES.addAll(frames);
            currentFrame = FRAMES.isEmpty() ? null : FRAMES.get(0);
            return FRAMES.size();
        }
    }

    public static void onRenderFrame(MinecraftClient client, Camera camera) {
        long now = System.nanoTime();
        synchronized (LOCK) {
            if (mode == Mode.RECORDING) {
                recordFrame(now, camera, client);
            } else if (mode == Mode.REPLAYING) {
                tickReplay(now, client);
            }
        }
    }

    private static void recordFrame(long now, Camera camera, MinecraftClient client) {
        if (camera == null) {
            return;
        }

        long delta = recordLastRealNano == 0L ? 0L : now - recordLastRealNano;
        recordLastRealNano = now;

        double timeScale = 1.0;
        if (Fpv20Client.in_slow_motion) {
            timeScale = Math.max(0.001, Fpv20Client.config1.slow_motion_time_rate);
        }
        long adjustedDelta = (long) (delta / timeScale);
        recordTimeNanos += adjustedDelta;

        Vec3d pos = ((CameraAccessor) camera).fpv20$getPos();
        float yaw = camera.getYaw();
        float pitch = camera.getPitch();
        Quaternionf rotation;
        if (GlobalFlying.getFlying()) {
            rotation = GlobalFlying.G.cacl_cam_rotation();
        } else {
            rotation = PhysicsCore.from_ypr_deg(yaw, pitch, 0.0f);
        }

        Vec3d vel = Vec3d.ZERO;
        if (GlobalFlying.getFlying()) {
            vel = GlobalFlying.G.getLastRawVelocity();
        } else if (client != null && client.getCameraEntity() != null) {
            vel = client.getCameraEntity().getVelocity();
        }
        float thrust = GlobalFlying.getFlying() ? GlobalFlying.G.getLastRawThrust() : 0.0f;
        ReplayFrame frame = new ReplayFrame(recordTimeNanos, pos.x, pos.y, pos.z, rotation,
                vel.x, vel.y, vel.z, thrust);
        FRAMES.add(frame);
        if (logWriter != null) {
            float tsMs = recordTimeNanos / 1_000_000.0f;
            try {
                logWriter.writeLine(tsMs, frame.x, frame.y, frame.z, frame.getRotation(),
                        frame.vx, frame.vy, frame.vz, frame.thrust);
            } catch (IOException e) {
                Fpv20.LOGGER.error("FPV replay: failed to write log line", e);
                closeLogWriter();
            }
        }
    }

    private static void tickReplay(long now, MinecraftClient client) {
        if (FRAMES.isEmpty()) {
            return;
        }

        if (replayLastPollNano == 0L) {
            replayLastPollNano = now;
        }

        while (now - replayLastPollNano >= replayStepNanos) {
            replayLastPollNano += replayStepNanos;
            long replayTime = (long) ((replayLastPollNano - replayStartRealNano) * replaySpeed);
            currentReplayTimeNanos = replayTime;
            updateFrameForTime(replayTime);
            if (mode != Mode.REPLAYING) {
                break;
            }
        }

        if (currentFrame != null) {
            applyFrame(client, currentFrame);
        }
    }

    private static void updateFrameForTime(long replayTime) {
        int lastIndex = FRAMES.size() - 1;
        ReplayFrame last = FRAMES.get(lastIndex);
        if (replayTime >= last.timeNanos) {
            currentFrame = last;
            frameRenderer.renderFrame(currentFrame);
            mode = Mode.IDLE;
            return;
        }

        ReplayFrame sampled = sampleFrame(replayTime);
        currentFrame = sampled;
        frameRenderer.renderFrame(sampled);
    }

    private static ReplayFrame sampleFrame(long replayTime) {
        if (playheadIndex < 0) {
            playheadIndex = 0;
        }
        int maxIndex = FRAMES.size() - 2;
        while (playheadIndex < maxIndex && FRAMES.get(playheadIndex + 1).timeNanos <= replayTime) {
            playheadIndex++;
        }

        ReplayFrame a = FRAMES.get(playheadIndex);
        ReplayFrame b = FRAMES.get(playheadIndex + 1);
        if (b.timeNanos == a.timeNanos) {
            return b;
        }
        float t = (float) ((replayTime - a.timeNanos) / (double) (b.timeNanos - a.timeNanos));
        int p0Index = Math.max(0, playheadIndex - 1);
        int p3Index = Math.min(FRAMES.size() - 1, playheadIndex + 2);
        return a.lerpSpline(FRAMES.get(p0Index), a, b, FRAMES.get(p3Index), t);
    }

    private static void applyFrame(MinecraftClient client, ReplayFrame frame) {
        if (client == null) {
            return;
        }
        Entity cameraEntity = client.getCameraEntity();
        if (cameraEntity == null) {
            return;
        }
        double targetY = frame.y;
        if (cameraEntity instanceof LivingEntity) {
            double eyeHeight = cameraEntity.getEyeY() - cameraEntity.getY();
            targetY = frame.y - eyeHeight;
        }
        cameraEntity.setPos(frame.x, targetY, frame.z);
        Vector3f ypr = PhysicsCore.from_quaternion_to_ypr_deg(frame.getRotation());
        cameraEntity.setYaw(ypr.x);
        cameraEntity.setPitch(ypr.y);
        cameraEntity.setVelocity(Vec3d.ZERO);
        if (cameraEntity instanceof EntityAccessor accessor) {
            accessor.fpv20$setLastX(frame.x);
            accessor.fpv20$setLastY(targetY);
            accessor.fpv20$setLastZ(frame.z);
            accessor.fpv20$setLastRenderX(frame.x);
            accessor.fpv20$setLastRenderY(targetY);
            accessor.fpv20$setLastRenderZ(frame.z);
            accessor.fpv20$setLastYaw(ypr.x);
            accessor.fpv20$setLastPitch(ypr.y);
        }

        if (client.player != null && client.player != cameraEntity) {
            double playerTargetY = frame.y;
            double eyeHeight = client.player.getEyeY() - client.player.getY();
            playerTargetY = frame.y - eyeHeight;
            client.player.setPos(frame.x, playerTargetY, frame.z);
            client.player.setYaw(ypr.x);
            client.player.setPitch(ypr.y);
            client.player.setVelocity(Vec3d.ZERO);
            if (client.player instanceof EntityAccessor playerAccessor) {
                playerAccessor.fpv20$setLastX(frame.x);
                playerAccessor.fpv20$setLastY(playerTargetY);
                playerAccessor.fpv20$setLastZ(frame.z);
                playerAccessor.fpv20$setLastRenderX(frame.x);
                playerAccessor.fpv20$setLastRenderY(playerTargetY);
                playerAccessor.fpv20$setLastRenderZ(frame.z);
                playerAccessor.fpv20$setLastYaw(ypr.x);
                playerAccessor.fpv20$setLastPitch(ypr.y);
            }
        }
    }

    private static int estimateRecordingFps() {
        if (FRAMES.size() < 2) {
            return MIN_REPLAY_FPS;
        }
        long duration = FRAMES.get(FRAMES.size() - 1).timeNanos;
        if (duration <= 0L) {
            return MIN_REPLAY_FPS;
        }
        double seconds = duration / 1_000_000_000.0;
        double fps = (FRAMES.size() - 1) / seconds;
        if (fps < MIN_REPLAY_FPS) {
            return MIN_REPLAY_FPS;
        }
        if (fps > 240.0) {
            return 240;
        }
        return (int) Math.round(fps);
    }

    private static void closeLogWriter() {
        if (logWriter != null) {
            try {
                logWriter.close();
            } catch (IOException e) {
                Fpv20.LOGGER.error("FPV replay: failed to close log writer", e);
            } finally {
                logWriter = null;
            }
        }
    }

    private static Path resolveRecordingDir() {
        MinecraftClient client = MinecraftClient.getInstance();
        String worldLabel = "unknown";
        if (client != null) {
            if (client.getServer() != null) {
                worldLabel = client.getServer().getSaveProperties().getLevelName();
            } else if (client.getCurrentServerEntry() != null) {
                worldLabel = client.getCurrentServerEntry().name;
                if (worldLabel == null || worldLabel.isBlank()) {
                    worldLabel = client.getCurrentServerEntry().address;
                }
            }
        }
        worldLabel = sanitizeName(worldLabel);
        String timestamp = LocalDateTime.now().format(TS_FORMAT);
        Path root = client != null ? client.runDirectory.toPath().resolve(DEFAULT_REPLAY_ROOT) : DEFAULT_REPLAY_ROOT;
        return root.resolve(worldLabel + "-" + timestamp);
    }

    private static String sanitizeName(String name) {
        if (name == null || name.isBlank()) {
            return "unknown";
        }
        String cleaned = name.trim().replaceAll("[^A-Za-z0-9._-]+", "_");
        if (cleaned.isBlank()) {
            return "unknown";
        }
        return cleaned;
    }
}
