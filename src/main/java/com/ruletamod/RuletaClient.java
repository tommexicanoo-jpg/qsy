package com.ruletamod;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/** Dibuja la ruleta en pantalla y la hace girar. */
public class RuletaClient implements ClientModInitializer {

    private static final ResourceLocation RUEDA =
            ResourceLocation.fromNamespaceAndPath(RuletaMod.MOD_ID, "textures/gui/wheel.png");
    private static final ResourceLocation PUNTERO =
            ResourceLocation.fromNamespaceAndPath(RuletaMod.MOD_ID, "textures/gui/pointer.png");

    /** Radio de la ruleta en pantalla (en píxeles de GUI). Cámbialo para hacerla más grande/pequeña. */
    private static final int RADIO = 80;

    private static RuletaEvent evento;
    private static float anguloFinal;
    private static int ticks;
    private static int ultimoSector;
    private static boolean sonidoFinal;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(SpinPayload.TYPE, (payload, ctx) ->
                ctx.client().execute(() -> {
                    evento = RuletaEvent.values()[Mth.clamp(payload.segmento(), 0, RuletaEvent.values().length - 1)];
                    // 5 vueltas completas + llevar el sector elegido hasta arriba (+ pequeño desfase)
                    anguloFinal = 360f * 5f - 45f * evento.ordinal() + payload.desfase();
                    ticks = 0;
                    ultimoSector = 0;
                    sonidoFinal = false;
                }));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (evento != null && !mc.isPaused()) {
                if (++ticks >= RuletaMod.TOTAL_TICKS) evento = null;
            }
        });

        HudRenderCallback.EVENT.register(RuletaClient::dibujar);
    }

    private static void dibujar(GuiGraphics g, DeltaTracker delta) {
        if (evento == null) return;
        Minecraft mc = Minecraft.getInstance();

        float t = ticks + (mc.isPaused() ? 0f : delta.getGameTimeDeltaPartialTick(false));
        float p = Mth.clamp(t / RuletaMod.GIRO_TICKS, 0f, 1f);
        float suave = 1f - (1f - p) * (1f - p) * (1f - p); // frena poco a poco
        float angulo = anguloFinal * suave;

        float alpha = 1f;
        float inicioFade = RuletaMod.TOTAL_TICKS - RuletaMod.DESVANECER_TICKS;
        if (t > inicioFade) alpha = Mth.clamp((RuletaMod.TOTAL_TICKS - t) / RuletaMod.DESVANECER_TICKS, 0f, 1f);

        // Sonidos: "clic" al pasar cada sector y campanita al terminar
        int sector = (int) Math.floor(angulo / 45f);
        if (p < 1f && sector != ultimoSector) {
            ultimoSector = sector;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.6f));
        }
        if (p >= 1f && !sonidoFinal) {
            sonidoFinal = true;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2f));
        }

        int cx = g.guiWidth() / 2;
        int cy = g.guiHeight() / 2 - 10;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1f, 1f, 1f, alpha);

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(angulo));
        g.blit(RUEDA, -RADIO, -RADIO, RADIO * 2, RADIO * 2, 0f, 0f, 512, 512, 512, 512);
        pose.popPose();

        // Puntero fijo arriba (28x35)
        g.blit(PUNTERO, cx - 14, cy - RADIO - 24, 28, 35, 0f, 0f, 128, 160, 128, 160);

        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();

        // Nombre del resultado cuando la ruleta se detiene
        if (p >= 1f) {
            int a = Math.max(8, (int) (alpha * 255f));
            pose.pushPose();
            pose.translate(cx, cy + RADIO + 12, 0);
            pose.scale(2f, 2f, 1f);
            g.drawCenteredString(mc.font, evento.titulo, 0, 0, (a << 24) | evento.color);
            pose.popPose();
        }
    }
}
