package com.iung.fpv20.replay;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import org.joml.Quaternionf;

public final class FpvReplayLogWriter implements AutoCloseable {
    private final BufferedWriter writer;
    private int linesSinceFlush;

    private FpvReplayLogWriter(OutputStream out) {
        this.writer = new BufferedWriter(new OutputStreamWriter(out));
    }

    public static FpvReplayLogWriter open(Path path) throws IOException {
        OutputStream out = Files.newOutputStream(path);
        GZIPOutputStream gzip = new GZIPOutputStream(out, true);
        return new FpvReplayLogWriter(gzip);
    }

    public void writeLine(float tsMs,
                          double x, double y, double z,
                          Quaternionf rotation,
                          double vx, double vy, double vz,
                          float thrust) throws IOException {
        StringBuilder sb = new StringBuilder(160);
        float rw = rotation.w();
        float rx = rotation.x();
        float ry = rotation.y();
        float rz = rotation.z();
        sb.append(floatToString(tsMs)).append(' ')
          .append(doubleToString(x)).append(' ')
          .append(doubleToString(y)).append(' ')
          .append(doubleToString(z)).append(' ')
          .append(floatToString(rw)).append(' ')
          .append(floatToString(rx)).append(' ')
          .append(floatToString(ry)).append(' ')
          .append(floatToString(rz)).append(' ')
          .append(doubleToString(vx)).append(' ')
          .append(doubleToString(vy)).append(' ')
          .append(doubleToString(vz)).append(' ')
          .append(floatToString(thrust))
          .append('\n');
        writer.write(sb.toString());
        linesSinceFlush++;
        if (linesSinceFlush >= 512) {
            writer.flush();
            linesSinceFlush = 0;
        }
    }

    private static String floatToString(float v) {
        return Float.toString(v);
    }

    private static String doubleToString(double v) {
        return Double.toString(v);
    }

    @Override
    public void close() throws IOException {
        writer.flush();
        writer.close();
    }
}
