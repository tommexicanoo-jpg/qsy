package com.ruletamod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;

public class RuletaMod implements ModInitializer {
    public static final String MOD_ID = "ruletamod";

    // ----- Tiempos (en ticks, 20 ticks = 1 segundo) -----
    public static final int GIRO_TICKS = 100;      // lo que tarda en girar (5 s)
    public static final int PAUSA_TICKS = 30;      // se queda quieta mostrando el resultado
    public static final int DESVANECER_TICKS = 10; // se desvanece
    public static final int TOTAL_TICKS = GIRO_TICKS + PAUSA_TICKS + DESVANECER_TICKS;
    /** El efecto se aplica justo cuando termina el giro + la pausa. */
    public static final int RETRASO_EFECTO_TICKS = GIRO_TICKS + PAUSA_TICKS;

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(SpinPayload.TYPE, SpinPayload.CODEC);

        ServerLifecycleEvents.SERVER_STARTED.register(RuletaManager::iniciar);
        ServerLifecycleEvents.SERVER_STOPPING.register(RuletaManager::detener);
        ServerTickEvents.END_SERVER_TICK.register(RuletaManager::tick);

        // /ruleta            -> gira una al azar
        // /ruleta roja       -> fuerza un sector concreto (para probar)
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralArgumentBuilder<CommandSourceStack> raiz = Commands.literal("ruleta")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> {
                        RuletaManager.girar(ctx.getSource().getServer(), null);
                        return 1;
                    });
            for (RuletaEvent e : RuletaEvent.values()) {
                raiz.then(Commands.literal(e.name().toLowerCase())
                        .executes(ctx -> {
                            RuletaManager.girar(ctx.getSource().getServer(), e);
                            return 1;
                        }));
            }
            dispatcher.register(raiz);
        });
    }
}
