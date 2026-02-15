package toflking.jerrytimer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;


public class JerryTimerClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("jerrytimer");

	private static String lastServer = null;

	private static final java.nio.file.Path CONFIG_PATH =
			net.fabricmc.loader.api.FabricLoader.getInstance()
					.getConfigDir()
					.resolve("jerrytimer.json");

	static void saveConfig() {
		try {
			String json = "{\"x\":" + hudX + ",\"y\":" + hudY + "}";
			java.nio.file.Files.writeString(CONFIG_PATH, json);
		} catch (Exception ignored) {}
	}

	private static void loadConfig() {
		try {
			if (!java.nio.file.Files.exists(CONFIG_PATH)) return;

			String json = java.nio.file.Files.readString(CONFIG_PATH);

			java.util.regex.Matcher m =
					java.util.regex.Pattern.compile("\"x\":(\\d+),\"y\":(\\d+)")
							.matcher(json);

			if (m.find()) {
				hudX = Integer.parseInt(m.group(1));
				hudY = Integer.parseInt(m.group(2));
			}

		} catch (Exception ignored) {}
	}

	static boolean editMode = false;
	private static boolean dragging = false;
	static int hudX = 10;
    static int hudY = 10;
	private static int dragOffX = 0, dragOffY = 0;

	private static int mouseXGui(Minecraft mc) {
		double mx = mc.mouseHandler.xpos(); // raw pixels
		return (int) (mx * mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth());
	}
	private static int mouseYGui(Minecraft mc) {
		double my = mc.mouseHandler.ypos();
		return (int) (my * mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight());
	}

	private static boolean leftMouseDown(Minecraft mc) {
		return mc.mouseHandler.isLeftPressed();
	}

	private static boolean pendingOpenMoveScreen = false;
	private static boolean pendingCloseMoveScreen = false;


	private static boolean titleShown = false;
	private static long lastResetMs = -1;

	private static final java.util.regex.Pattern MC_FORMATTING =
			java.util.regex.Pattern.compile("§[0-9A-FK-ORa-fk-or]");

	private static String normalize(String s) {
		s = MC_FORMATTING.matcher(s).replaceAll("");
		s = s.replaceAll("[\\p{Cf}\\p{Cc}]", "");
		s = s.replace('\u00A0', ' ');
		s = s.replaceAll("[^\\x20-\\x7E]", "");
		s = s.replaceAll("\\s+", " ").trim();
		return s;
	}


	private static String stripFormatting(String s) {
		return MC_FORMATTING.matcher(s).replaceAll("");
	}

	private static final java.util.List<String> JERRY_TYPES = java.util.List.of(
			"Green",
			"Blue",
			"Purple",
			"Golden"
	);
	private static final java.util.List<String> JERRY_TEMPLATES = java.util.List.of(
			"You discovered a %s!",
			"There is a %s!",
			"You found a %s!",
			"You located a hidden %s!",
			"A wild %s spawned!",
			"Some %s was hiding, but you found it!",
			"A %s appeared!"
	);

	private static final java.util.Set<String> TRIGGERS;

	static {
		java.util.Set<String> set = new java.util.HashSet<>();
		for (String t : JERRY_TEMPLATES) {
			for (String type : JERRY_TYPES) {
				set.add(String.format(t, type));
			}
		}
		TRIGGERS = java.util.Collections.unmodifiableSet(set);
	}

	private static void handle(String message) {
		String plain = normalize(stripFormatting(message));

		for (String type : JERRY_TYPES) {
			if (plain.contains(type) && plain.contains("Jerry") && plain.contains("!") && !plain.contains(":")){

				ChatFormatting color = switch (type) {
					case "Green" -> ChatFormatting.GREEN;
					case "Blue" -> ChatFormatting.BLUE;
					case "Purple" -> ChatFormatting.DARK_PURPLE;
					case "Golden" -> ChatFormatting.GOLD;
					default -> ChatFormatting.WHITE;
				};

				Minecraft mc = Minecraft.getInstance();
				mc.gui.setTitle(
						Component.literal(type + " Jerry spawned")
								.withStyle(color)
				);




				lastResetMs = System.currentTimeMillis();
				titleShown = false;

				break;
			}
		}
	}


	@Override
	public void onInitializeClient() {
		loadConfig();
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {

			String current = client.getCurrentServer().ip.toLowerCase();

			boolean isHypixel = current.contains("hypixel");
			boolean wasHypixel = lastServer != null && lastServer.toLowerCase().contains("hypixel");

			if (!isHypixel || !wasHypixel) {
				lastResetMs = -1;
			}

			lastServer = current;

		});

		ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
			handle(message.getString());
		});

		ClientReceiveMessageEvents.GAME.register(((message, overlay) -> {
			handle(message.getString());
		}));


		HudRenderCallback.EVENT.register((GuiGraphics graphics, net.minecraft.client.DeltaTracker tickDelta) -> {
			if (lastResetMs < 0) return;

			long elapsedMs = System.currentTimeMillis() - lastResetMs;
			long totalseconds = elapsedMs / 1000;
			long minutes = totalseconds / 60;
			long seconds = totalseconds % 60;

			String text = String.format("%d:%02d", minutes, seconds);

			Minecraft mc = Minecraft.getInstance();
			Font font = mc.font;
			if (font == null) return;

			int x = hudX, y = hudY;
			if (minutes < 6) {
				graphics.drawString(font, text, x, y, 0xFFFF0000, true);
			} else {
				graphics.drawString(font, text, x, y, 0xFF00FF00, true);
				if (minutes == 6 && seconds == 0 && !titleShown) {
					mc.gui.setTitle(Component.literal("Jerry Cooldown fertig")
							.withStyle(ChatFormatting.GOLD));
				}
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (pendingOpenMoveScreen) {
				pendingOpenMoveScreen = false;
				client.setScreen(new MoveHudScreen());
				client.mouseHandler.releaseMouse();
				System.out.println("OPENED SCREEN: " + client.screen);
			}


			if (pendingCloseMoveScreen) {
				pendingCloseMoveScreen = false;
				if (client.screen instanceof MoveHudScreen) {
					client.screen.onClose();
				}
				client.mouseHandler.grabMouse();
			}

			if (!editMode) { dragging = false; return; }
			if (client.player == null) { dragging = false; return; }
			if (!(client.screen instanceof MoveHudScreen)) { dragging = false; return; }


			boolean down = leftMouseDown(client);
			int mx = mouseXGui(client);
			int my = mouseYGui(client);

			int w = client.font.width("88:88");
			int h = client.font.lineHeight;

			boolean inside = mx >= hudX && mx <= hudX + w && my >= hudY && my <= hudY + h;

			if (!dragging) {
				if (down && inside) {
					dragging = true;
					dragOffX = mx - hudX;
					dragOffY = my - hudY;
				}
			} else {
				if (!down) {
					dragging = false;

				} else {
					hudX = mx - dragOffX;
					hudY = my - dragOffY;


					int maxX = client.getWindow().getGuiScaledWidth() - w;
					int maxY = client.getWindow().getGuiScaledHeight() - h;
					hudX = Math.max(0, Math.min(hudX, maxX));
					hudY = Math.max(0, Math.min(hudY, maxY));
				}
			}
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
			dispatcher.register(
					literal("jerrytimer")
							.executes(ctx -> {
								editMode = !editMode;

								if (editMode) pendingOpenMoveScreen = true;
								else pendingCloseMoveScreen = true;

								ctx.getSource().sendFeedback(
										Component.literal("Jerrytimer edit mode")
								);
                                return 1;
                            })
			);
		});
	}
}