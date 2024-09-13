package com.minenash.oplock;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static net.minecraft.server.command.CommandManager.literal;

public class OpLock implements ModInitializer {

	public static final Logger LOGGER = LogManager.getLogger("OpLock");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path OP_PATH = FabricLoader.getInstance().getConfigDir().resolve("oplock.json");
	private static final Path ASI_PATH = FabricLoader.getInstance().getConfigDir().resolve("oplock_auto_sign_in.json");

	public static final Set<UUID> autoSignIn = new HashSet<>();
	public static final Map<UUID,Boolean> opStatus = new HashMap<>();
	public static MinecraftServer server;

	@Override
	public void onInitialize() {

		ServerLifecycleEvents.SERVER_STARTING.register(OpLock::load);

		ServerPlayConnectionEvents.JOIN.register(((handler, _sender, _server) -> autoLogout(handler.player.getGameProfile())));
		ServerPlayConnectionEvents.DISCONNECT.register(((handler, _server) -> autoLogout(handler.player.getGameProfile())));

		CommandRegistrationCallback.EVENT.register(((dispatcher, commandRegistryAccess, environment) -> dispatcher.register(
				literal("oplock").requires(OpLock::canRunCommand)
						.then( literal("login").executes(OpLock::login))
						.then( literal("logout").executes(OpLock::logout))
						.then( literal("refresh").executes(OpLock::refresh) )
		)));
	}

	private static boolean canRunCommand(ServerCommandSource source) {
		try {
			return opStatus.containsKey(source.getPlayer().getUuid());
		}
		catch (Exception e) {
			return false;
		}
	}

	private static int login(CommandContext<ServerCommandSource> context) {
		ServerPlayerEntity player = context.getSource().getPlayer();
		opStatus.put(player.getUuid(), true);
		server.getPlayerManager().addToOperators(player.getGameProfile());
		player.sendMessage( Text.literal("§8[§2OpLock§8]§a Logged In, you now have op powers"), false);
        LOGGER.info("[OpLock] {} logged in", player.getGameProfile().getName());
		return 1;
	}

	private static int logout(CommandContext<ServerCommandSource> context) {
		ServerPlayerEntity player = context.getSource().getPlayer();
		opStatus.put(context.getSource().getPlayer().getUuid(), false);
		server.getPlayerManager().removeFromOperators(player.getGameProfile());
		player.sendMessage( Text.literal("§8[§2OpLock§8]§a Logged Out, you now no longer have op powers"), false);
        LOGGER.info("[OpLock] {} logged out", player.getGameProfile().getName());
		return 1;
	}

	private static int refresh(CommandContext<ServerCommandSource> context) {
		ServerPlayerEntity player = context.getSource().getPlayer();
		loadAutoSignIn();


		player.sendMessage( Text.literal("§8[§2OpLock§8]§a Config refreshed"), false);
        LOGGER.info("[OpLock] Config was refreshed by {}", player.getGameProfile().getName());
		return 1;
	}

	private static void autoLogout(GameProfile profile) {
		if (opStatus.containsKey(profile.getId()) || server.getPlayerManager().isOperator(profile)) {
			if (autoSignIn.contains(profile.getId())) {
				opStatus.put(profile.getId(), true);
				server.getPlayerManager().addToOperators(profile);
			}
			else {
				opStatus.put(profile.getId(), false);
				server.getPlayerManager().removeFromOperators(profile);
			}
			save();
		}
	}

	private static void loadAutoSignIn() {
		if (!Files.exists(ASI_PATH)) {
			try {
				Files.createFile(ASI_PATH);
			}
			catch (Exception e) {
				LOGGER.error("[OpLock] Failed to create oplock_auto_sign_in.json");
				LOGGER.catching(e);
			}
			return;
		}

		try {
			JsonElement element = GSON.fromJson(Files.newBufferedReader(ASI_PATH), JsonElement.class);
			if (element.isJsonArray()) {
				autoSignIn.clear();
				for (JsonElement entry : element.getAsJsonArray())
					if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString())
						autoSignIn.add(UUID.fromString(entry.getAsString()));
			}
		}
		catch (Exception e) {
			LOGGER.error("[OpLock] Failed to load oplock_auto_sign_in.json");
			LOGGER.catching(e);
		}

	}

	private static void load(MinecraftServer server) {
		OpLock.server = server;
		loadAutoSignIn();
		if (!Files.exists(OP_PATH)) {
			save();
			return;
		}

		try {
			JsonElement element = GSON.fromJson(Files.newBufferedReader(OP_PATH), JsonElement.class);
			if (element.isJsonArray()) {
				for (JsonElement entry : element.getAsJsonArray())
					if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
						opStatus.put(UUID.fromString(entry.getAsString()), false);
					}
			}
		}
		catch (Exception e) {
			LOGGER.error("[OpLock] Failed to load op list");
			LOGGER.catching(e);
		}

	}

	public static void save() {
		if (!Files.exists(OP_PATH)) {
			try {
				Files.createFile(OP_PATH);
			} catch (IOException e) {
				LOGGER.error("[OpLock] Failed to save op list");
				LOGGER.catching(e);
				return;
			}
		}

		JsonArray ops = new JsonArray();
		for (UUID entry : opStatus.keySet())
			ops.add(entry.toString());

		try {
			Files.write(OP_PATH, GSON.toJson(ops).getBytes());
		} catch (IOException e) {
			LOGGER.error("[OpLock] Failed to save op list");
			LOGGER.catching(e);
		}
	}

}
