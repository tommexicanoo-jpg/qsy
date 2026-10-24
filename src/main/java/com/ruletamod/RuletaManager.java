package com.ruletamod;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

/** Toda la lógica del servidor: temporizador, elección de sector y efectos. */
public final class RuletaManager {

    private static final Random RNG = new Random();

    /** Duración del "momento revi": 3 minutos. */
    private static final int REVI_TICKS = 3 * 60 * 20;

    private static int ticksParaGirar;
    private static RuletaEvent pendiente;
    private static int retraso;

    private static int revi;               // ticks restantes del momento revi (0 = inactivo)
    private static Difficulty difPrevia = Difficulty.NORMAL;
    private static ServerBossEvent barra;

    private RuletaManager() {}

    // ------------------------------------------------------------------ ciclo de vida

    public static void iniciar(MinecraftServer server) {
        pendiente = null;
        revi = 0;
        barra = new ServerBossEvent(
                Component.literal("¡MOMENTO REVI!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.NOTCHED_10);
        programar();
    }

    public static void detener(MinecraftServer server) {
        if (revi > 0) {
            server.setDifficulty(difPrevia, true);
            revi = 0;
        }
        if (barra != null) barra.removeAllPlayers();
        pendiente = null;
    }

    /** Siguiente ruleta en 2, 3, 4 o 5 minutos (al azar). */
    private static void programar() {
        ticksParaGirar = (2 + RNG.nextInt(4)) * 60 * 20;
    }

    public static void tick(MinecraftServer server) {
        if (barra == null || server.getPlayerList().getPlayerCount() == 0) return;

        if (pendiente != null) {
            if (--retraso <= 0) {
                RuletaEvent e = pendiente;
                pendiente = null;
                aplicar(server, e);
            }
        } else if (--ticksParaGirar <= 0) {
            girar(server, null);
        }

        if (revi > 0) tickRevi(server);
    }

    // ------------------------------------------------------------------ giro

    public static void girar(MinecraftServer server, RuletaEvent forzado) {
        if (pendiente != null || barra == null) return;

        RuletaEvent e = forzado != null ? forzado : RuletaEvent.values()[RNG.nextInt(RuletaEvent.values().length)];
        float desfase = (RNG.nextFloat() * 2f - 1f) * 17f; // cae en cualquier punto del sector
        pendiente = e;
        retraso = RuletaMod.RETRASO_EFECTO_TICKS;
        programar();

        SpinPayload paquete = new SpinPayload(e.ordinal(), desfase);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, paquete);
        }
    }

    // ------------------------------------------------------------------ efectos

    private static void aplicar(MinecraftServer server, RuletaEvent e) {
        switch (e) {
            case NARANJA -> cambioDificultad(server);
            case ROJA -> iniciarRevi(server);
            case ROSA -> efectoLoco(server);
            case MORADA -> teletransporte(server);
            case AZUL -> climaYHora(server);
            case CIAN -> bendicionOMaldicion(server);
            case VERDE -> aviso(server);
            case AMARILLA -> regalo(server);
        }
    }

    // NARANJA ---------------------------------------------------------------
    private static void cambioDificultad(MinecraftServer server) {
        boolean enRevi = revi > 0;
        Difficulty actual = enRevi ? difPrevia : server.getWorldData().getDifficulty();
        List<Difficulty> opciones = new ArrayList<>(List.of(Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD));
        opciones.remove(actual);
        Difficulty nueva = opciones.get(RNG.nextInt(opciones.size()));

        if (enRevi) {
            difPrevia = nueva; // se aplicará cuando termine el momento revi
            anunciar(server, RuletaEvent.NARANJA,
                    "Nueva dificultad: " + nombreDificultad(nueva) + " (se aplica al terminar el momento revi)");
        } else {
            server.setDifficulty(nueva, true);
            anunciar(server, RuletaEvent.NARANJA, "Nueva dificultad: " + nombreDificultad(nueva));
        }
    }

    private static String nombreDificultad(Difficulty d) {
        return switch (d) {
            case PEACEFUL -> "Pacífico";
            case EASY -> "Fácil";
            case NORMAL -> "Normal";
            case HARD -> "Difícil";
        };
    }

    // ROJA ------------------------------------------------------------------
    private static void iniciarRevi(MinecraftServer server) {
        if (revi <= 0) {
            difPrevia = server.getWorldData().getDifficulty();
        }
        revi = REVI_TICKS;
        server.setDifficulty(Difficulty.HARD, true);
        anunciar(server, RuletaEvent.ROJA, "¡Durante 3 minutos habrá muchísimos monstruos! Sobrevive.");
    }

    private static void tickRevi(MinecraftServer server) {
        revi--;
        barra.setProgress(Math.max(0f, revi / (float) REVI_TICKS));
        List<ServerPlayer> jugadores = server.getPlayerList().getPlayers();
        for (ServerPlayer p : jugadores) barra.addPlayer(p);

        if (revi % 60 == 0) {
            for (ServerPlayer p : jugadores) generarMobs(p, 2);
        }

        if (revi <= 0) {
            barra.removeAllPlayers();
            server.setDifficulty(difPrevia, true);
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("[Ruleta] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                            .append(Component.literal("Terminó el momento revi. Respira...").withStyle(ChatFormatting.GREEN)),
                    false);
        }
    }

    private static void generarMobs(ServerPlayer p, int cantidad) {
        ServerLevel nivel = p.serverLevel();
        if (nivel.getEntitiesOfClass(Monster.class, p.getBoundingBox().inflate(32)).size() >= 25) return;

        List<EntityType<? extends Mob>> tipos = List.of(
                EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.SKELETON,
                EntityType.SPIDER, EntityType.CREEPER, EntityType.WITCH);

        for (int n = 0; n < cantidad; n++) {
            for (int intento = 0; intento < 12; intento++) {
                int bx = p.getBlockX() + RNG.nextInt(29) - 14;
                int bz = p.getBlockZ() + RNG.nextInt(29) - 14;
                int by = p.getBlockY() + RNG.nextInt(9) - 4;
                if (Math.abs(bx - p.getBlockX()) < 6 && Math.abs(bz - p.getBlockZ()) < 6) continue;

                BlockPos pos = new BlockPos(bx, by, bz);
                BlockPos abajo = pos.below();
                if (!nivel.getBlockState(pos).isAir()
                        || !nivel.getBlockState(pos.above()).isAir()
                        || !nivel.getBlockState(abajo).isFaceSturdy(nivel, abajo, Direction.UP)) continue;

                Mob mob = tipos.get(RNG.nextInt(tipos.size())).create(nivel);
                if (mob == null) break;
                mob.moveTo(bx + 0.5, by, bz + 0.5, RNG.nextFloat() * 360f, 0f);
                mob.finalizeSpawn(nivel, nivel.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
                if (mob instanceof Zombie || mob instanceof AbstractSkeleton) {
                    // casco para que no se quemen de día
                    mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                    mob.setDropChance(EquipmentSlot.HEAD, 0f);
                }
                mob.setTarget(p);
                nivel.addFreshEntityWithPassengers(mob);
                break;
            }
        }
    }

    // ROSA ------------------------------------------------------------------
    private record Fx(String nombre, Holder<MobEffect> efecto, int segundos, int amp) {}

    private static void efectoLoco(MinecraftServer server) {
        List<Fx> lista = List.of(
                new Fx("Salto gigante", MobEffects.JUMP, 60, 5),
                new Fx("Súper velocidad", MobEffects.MOVEMENT_SPEED, 60, 3),
                new Fx("Todos brillan", MobEffects.GLOWING, 60, 0),
                new Fx("Visión nocturna", MobEffects.NIGHT_VISION, 120, 0),
                new Fx("Levitación", MobEffects.LEVITATION, 8, 0),
                new Fx("Invisibilidad", MobEffects.INVISIBILITY, 60, 0),
                new Fx("Prisa minera", MobEffects.DIG_SPEED, 60, 3),
                new Fx("Cámara lenta", MobEffects.MOVEMENT_SLOWDOWN, 20, 2),
                new Fx("Ceguera", MobEffects.BLINDNESS, 6, 0));
        Fx fx = lista.get(RNG.nextInt(lista.size()));

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.addEffect(new MobEffectInstance(fx.efecto(), fx.segundos() * 20, fx.amp()));
            if (fx.efecto() == MobEffects.LEVITATION) {
                // para que no mueras al caer
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30 * 20, 0));
            }
        }
        anunciar(server, RuletaEvent.ROSA, fx.nombre() + " para todos (" + fx.segundos() + "s)");
    }

    // MORADA ----------------------------------------------------------------
    private static void teletransporte(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerLevel nivel = p.serverLevel();
            if (nivel.dimensionType().hasCeiling()) {
                // En el Nether no es seguro: te damos perlas de ender de consolación
                p.getInventory().add(new ItemStack(Items.ENDER_PEARL, 3));
                continue;
            }
            for (int i = 0; i < 12; i++) {
                double a = RNG.nextDouble() * Math.PI * 2;
                int dist = 40 + RNG.nextInt(81);
                int x = p.getBlockX() + (int) (Math.cos(a) * dist);
                int z = p.getBlockZ() + (int) (Math.sin(a) * dist);
                if (!nivel.getWorldBorder().isWithinBounds(x, z)) continue;

                nivel.getChunk(x >> 4, z >> 4); // fuerza la carga del chunk
                int y = nivel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (!nivel.getBlockState(new BlockPos(x, y - 1, z)).getFluidState().isEmpty()) continue;

                p.teleportTo(nivel, x + 0.5, y, z + 0.5, p.getYRot(), p.getXRot());
                p.resetFallDistance();
                break;
            }
        }
        anunciar(server, RuletaEvent.MORADA, "¡Todos fueron enviados a un lugar aleatorio!");
    }

    // AZUL ------------------------------------------------------------------
    private static void climaYHora(MinecraftServer server) {
        ServerLevel mundo = server.overworld();
        String detalle;
        switch (RNG.nextInt(5)) {
            case 0 -> { mundo.setWeatherParameters(0, 6000, true, false); detalle = "Empieza a llover"; }
            case 1 -> { mundo.setWeatherParameters(0, 6000, true, true); detalle = "¡Tormenta eléctrica!"; }
            case 2 -> { mundo.setWeatherParameters(6000, 0, false, false); detalle = "Cielo despejado"; }
            case 3 -> { mundo.setDayTime(18000); detalle = "Ahora es medianoche"; }
            default -> { mundo.setDayTime(1000); detalle = "Amanece de golpe"; }
        }
        anunciar(server, RuletaEvent.AZUL, detalle);
    }

    // CIAN ------------------------------------------------------------------
    private static void bendicionOMaldicion(MinecraftServer server) {
        boolean bendicion = RNG.nextBoolean();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (bendicion) {
                p.heal(20f);
                p.getFoodData().setFoodLevel(20);
                p.getFoodData().setSaturation(10f);
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 20, 1));
                p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 60 * 20, 2));
            } else {
                p.addEffect(new MobEffectInstance(MobEffects.HUNGER, 30 * 20, 2));
                p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30 * 20, 0));
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 20, 1));
            }
        }
        anunciar(server, RuletaEvent.CIAN, bendicion
                ? "Bendición: vida, comida, regeneración y absorción"
                : "Maldición: hambre, debilidad y lentitud");
    }

    // VERDE -----------------------------------------------------------------
    private static void aviso(MinecraftServer server) {
        List<Function<ServerPlayer, String>> avisos = List.of(
                p -> "Tus coordenadas: " + p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ(),
                p -> "Tienes " + (int) p.getHealth() + " de vida y " + p.getFoodData().getFoodLevel() + " de hambre.",
                p -> "La próxima ruleta sale en unos " + Math.max(1, (ticksParaGirar + 1199) / 1200) + " minuto(s).",
                p -> p.serverLevel().isDay() ? "Ahora es de día... disfrútalo." : "Ahora es de noche, ¡cuidado!",
                p -> "Si sale la roja, necesitarás armas, comida y un buen refugio.",
                p -> "Consejo: lleva siempre comida, la ruleta es impredecible."
        );
        int idx = RNG.nextInt(avisos.size());
        anunciar(server, RuletaEvent.VERDE, null);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.literal(avisos.get(idx).apply(p)).withStyle(ChatFormatting.GREEN));
        }
    }

    // AMARILLA --------------------------------------------------------------
    private static void regalo(MinecraftServer server) {
        List<ItemStack> premios = List.of(
                new ItemStack(Items.GOLDEN_APPLE, 2),
                new ItemStack(Items.COOKED_BEEF, 10),
                new ItemStack(Items.IRON_INGOT, 8),
                new ItemStack(Items.DIAMOND, 2),
                new ItemStack(Items.ENDER_PEARL, 3),
                new ItemStack(Items.ARROW, 24),
                new ItemStack(Items.EXPERIENCE_BOTTLE, 10),
                new ItemStack(Items.SHIELD, 1),
                new ItemStack(Items.TORCH, 32));
        ItemStack premio = premios.get(RNG.nextInt(premios.size()));

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ItemStack copia = premio.copy();
            if (!p.getInventory().add(copia)) p.drop(copia, false);
        }
        anunciar(server, RuletaEvent.AMARILLA,
                "Todos reciben: " + premio.getCount() + "x " + premio.getHoverName().getString());
    }

    // ------------------------------------------------------------------ chat

    private static void anunciar(MinecraftServer server, RuletaEvent e, String detalle) {
        MutableComponent titulo = Component.literal("[Ruleta] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(e.titulo).withStyle(Style.EMPTY.withColor(e.color).withBold(true)));
        server.getPlayerList().broadcastSystemMessage(titulo, false);
        if (detalle != null) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal(detalle).withStyle(ChatFormatting.GRAY), false);
        }
    }
}
