package toflking.jerrytimer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import static toflking.jerrytimer.JerryTimerClient.hudX;
import static toflking.jerrytimer.JerryTimerClient.hudY;

public class MoveHudScreen extends Screen {
    private boolean dragging = false;
    private int dragOffX = 0, dragOffY = 0;
    private final String preview = "12:34";

    public MoveHudScreen() {
        super(Component.literal("Move HUD"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) return;
        int w = mc.font.width(preview);
        int h = mc.font.lineHeight;
        graphics.fill(hudX - 2, hudY - 2, hudX + w + 2, hudY + h + 2, 0x80FFFFFF);
        graphics.drawString(mc.font, preview, hudX, hudY, 0xFFFFFFFF, true);
        graphics.drawString(mc.font, "Drag the timer. Esc to exit.", 10, 10, 0xFFFFFFFF, true);
    }

    @Override
    public void onClose() {
        JerryTimerClient.editMode = false;
        Minecraft.getInstance().mouseHandler.grabMouse();
        super.onClose();
    }

    private boolean insideBox(int mx, int my) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) return false;
        int w = mc.font.width(preview);
        int h = mc.font.lineHeight;
        return mx >= hudX && mx <= hudX + w && my >= hudY && my <= hudY + h;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent ev, boolean bl) {
        int mx = (int) ev.x();
        int my = (int) ev.y();

        if (ev.button() == 0 && insideBox(mx, my)) { // 0 = linke Maustaste
            dragging = true;
            dragOffX = mx - hudX;
            dragOffY = my - hudY;
            return true;
        }
        return super.mouseClicked(ev, bl);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent ev, double dx, double dy) {
        if (dragging && ev.button() == 0) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.font == null) return true;

            int mx = (int) ev.x();
            int my = (int) ev.y();

            int w = mc.font.width(preview);
            int h = mc.font.lineHeight;

            hudX = mx - dragOffX;
            hudY = my - dragOffY;

            int maxX = mc.getWindow().getGuiScaledWidth() - w;
            int maxY = mc.getWindow().getGuiScaledHeight() - h;
            hudX = Math.max(0, Math.min(hudX, maxX));
            hudY = Math.max(0, Math.min(hudY, maxY));

            return true;
        }
        return super.mouseDragged(ev, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent ev) {
        if (ev.button() == 0) dragging = false;
        JerryTimerClient.saveConfig();
        return super.mouseReleased(ev);
    }
}
