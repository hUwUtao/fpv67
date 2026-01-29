package com.iung.fpv20.replay;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.joml.Quaternionf;
import com.iung.fpv20.physics.PhysicsCore;
import org.joml.Vector3f;

public final class FpvReplayLogParser {
    private FpvReplayLogParser() {
    }

    public static List<ReplayFrame> parse(Path path) throws IOException {
        List<ReplayFrame> frames = new ArrayList<>();
        try (InputStream in = Files.newInputStream(path);
             GZIPInputStream gzip = new GZIPInputStream(in);
             BufferedReader reader = new BufferedReader(new InputStreamReader(gzip))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\s+");
                if (parts.length < 11) {
                    continue;
                }
                try {
                    float tsMs = Float.parseFloat(parts[0]);
                    double x = Double.parseDouble(parts[1]);
                    double y = Double.parseDouble(parts[2]);
                    double z = Double.parseDouble(parts[3]);
                    Quaternionf rot;
                    double vx;
                    double vy;
                    double vz;
                    float thrust;
                    if (parts.length >= 12) {
                        float rw = Float.parseFloat(parts[4]);
                        float rx = Float.parseFloat(parts[5]);
                        float ry = Float.parseFloat(parts[6]);
                        float rz = Float.parseFloat(parts[7]);
                        rot = new Quaternionf(rx, ry, rz, rw);
                        vx = Double.parseDouble(parts[8]);
                        vy = Double.parseDouble(parts[9]);
                        vz = Double.parseDouble(parts[10]);
                        thrust = Float.parseFloat(parts[11]);
                    } else {
                        float yaw = Float.parseFloat(parts[4]);
                        float pitch = Float.parseFloat(parts[5]);
                        float roll = Float.parseFloat(parts[6]);
                        Vector3f ypr = new Vector3f(yaw, pitch, roll);
                        rot = PhysicsCore.from_ypr_deg(ypr.x, ypr.y, ypr.z);
                        vx = Double.parseDouble(parts[7]);
                        vy = Double.parseDouble(parts[8]);
                        vz = Double.parseDouble(parts[9]);
                        thrust = Float.parseFloat(parts[10]);
                    }
                    long timeNanos = Math.round(tsMs * 1_000_000.0);
                    frames.add(new ReplayFrame(timeNanos, x, y, z, rot, vx, vy, vz, thrust));
                } catch (NumberFormatException ignored) {
                    // Skip malformed lines.
                }
            }
        }
        return frames;
    }
}
