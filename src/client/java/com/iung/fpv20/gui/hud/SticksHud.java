package com.iung.fpv20.gui.hud;

import com.iung.fpv20.Fpv20Client;
import com.iung.fpv20.flying.GlobalFlying;
import com.iung.fpv20.input.Controller;
import com.iung.fpv20.replay.FpvReplayManager;
import com.iung.fpv20.utils.Utils;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

import java.util.Objects;

public class SticksHud implements HudRenderCallback {
    private ValueProvider t;
    private ValueProvider y;
    private ValueProvider p;
    private ValueProvider r;
    private int size;
    private int padding_between;
    private int padding_down = 10;
    private static final int WHITE = 0Xffffffff;

    public SticksHud() {
        this.size = 40;
        this.padding_between = 10;
        this.padding_down = 11;


        this.t = () -> Utils.requireNonNullOr(Fpv20Client.controller, c -> c.get_calibrated_value_no_rate(c.get_channel_id("t")), 0f);
        this.y = () -> Utils.requireNonNullOr(Fpv20Client.controller, c -> c.get_calibrated_value_no_rate(c.get_channel_id("y")), 0f);
        this.p = () -> Utils.requireNonNullOr(Fpv20Client.controller, c -> c.get_calibrated_value_no_rate(c.get_channel_id("p")), 0f);
        this.r = () -> Utils.requireNonNullOr(Fpv20Client.controller, c -> c.get_calibrated_value_no_rate(c.get_channel_id("r")), 0f);
    }


    @Override
    public void onHudRender(DrawContext drawContext, RenderTickCounter tickCounter) {
        if (!Fpv20Client.config.show_osd()) {
            return;
        }

        if (FpvReplayManager.isReplaying()) {
            long tMs = FpvReplayManager.getReplayTimeMillis();
            long totalSeconds = tMs / 1000;
            long minutes = totalSeconds / 60;
            long seconds = totalSeconds % 60;
            long millis = tMs % 1000;
            String timecode = String.format("%02d:%02d.%03d", minutes, seconds, millis);
            drawContext.drawText(MinecraftClient.getInstance().textRenderer,
                    Text.literal("REPLAY " + timecode), 6, 6, WHITE, true);
        }

        if (!GlobalFlying.getFlying()) {
            return;
        }
//        int size = 100;
//        int padding_between = 10;
//        int padding_down = 10;


        int window_width = drawContext.getScaledWindowWidth();
        int window_height = drawContext.getScaledWindowHeight();

        int start_x_1 = window_width / 2 - padding_between / 2 - size;
        int start_x_2 = window_width / 2 + padding_between / 2;

        int start_y = window_height - padding_down - size;


//        drawContext.fill(start_x_1, start_y, start_x_1 + size, start_y + size, WHITE);
//        drawContext.fill(start_x_2, start_y, start_x_2 + size, start_y + size, WHITE);

        drawContext.drawVerticalLine(start_x_1 + size / 2, start_y, start_y + size, WHITE);
        drawContext.drawVerticalLine(start_x_2 + size / 2, start_y, start_y + size, WHITE);

        drawContext.drawHorizontalLine(start_x_1, start_x_1 + size, start_y + size / 2, WHITE);
        drawContext.drawHorizontalLine(start_x_2, start_x_2 + size, start_y + size / 2, WHITE);

        fill_centered(drawContext, start_x_1 + size / 2 + y(), start_y + size / 2 - t(), 2, WHITE);
        fill_centered(drawContext, start_x_2 + size / 2 + r(), start_y + size / 2 - p(), 2, WHITE);
    }


    int t() {
        return Fpv20Client.config1.throttle_display_in_center ?
                Math.round(this.t.get() * size / 2f)
                : Math.round(this.t.get() * size - size / 2f);
    }

    int y() {
        return Math.round(this.y.get() * size / 2);
    }

    int p() {
        return Math.round(this.p.get() * size / 2);
    }

    int r() {
        return Math.round(this.r.get() * size / 2);
    }

    static void fill_centered(DrawContext drawContext, int x, int y, int r, int color) {
        drawContext.fill(x - r, y - r, x + r, y + r, color);
    }


    @FunctionalInterface
    public interface ValueProvider {
        float get();
    }
}
