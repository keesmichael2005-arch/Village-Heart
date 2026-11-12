package dev.villageheart.gametest;

import static dev.villageheart.gametest.WorkerTestSupport.begin;
import static dev.villageheart.gametest.WorkerTestSupport.end;

import dev.villageheart.core.siege.ThreatWarnings;
import dev.villageheart.raid.ThreatMessages;
import io.netty.channel.ChannelFutureListener;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Layer 2 for the owner's decision of 2026-11-12, read off the REAL packets a player's connection receives (the real chat/title sink, not
 * the recording one): the lead-up lines are gold chat; a raid start is the big title plus one bold red chat line; a siege start is one
 * bold red chat line and no title packet at all. {@code ThreatShelterGameTests} proves the real raid and siege starts call this same path.
 */
public class ThreatStartMessageGameTests {
	private static final String YARD = BuildingTestSupport.YARD;

	private static final class Capture extends Connection {
		final List<Packet<?>> sent = new ArrayList<>();

		Capture() { super(PacketFlow.SERVERBOUND); }

		@Override
		public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) {
			sent.add(packet);
			super.send(packet, listener, flush);
		}
	}

	/** A survival player next to the Heart whose connection records every packet from here on. */
	private static Capture watcher(GameTestHelper h) {
		Capture capture = new Capture();
		ServerPlayer p = VillageTestSupport.player(h, "watcher", GameType.SURVIVAL,
				(server, level, profile) -> new ServerPlayer(server, level, profile, ClientInformation.createDefault()), capture);
		BlockPos at = h.absolutePos(new BlockPos(3, 1, 3));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		capture.sent.clear();
		return capture;
	}

	private static String keyOf(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : "<" + c.getString() + ">";
	}

	private static List<ClientboundSystemChatPacket> chat(Capture c, String key) {
		List<ClientboundSystemChatPacket> out = new ArrayList<>();
		for (Packet<?> p : c.sent) if (p instanceof ClientboundSystemChatPacket s && keyOf(s.content()).equals(key)) out.add(s);
		return out;
	}

	private static long titles(Capture c) {
		return c.sent.stream().filter(p -> p instanceof ClientboundSetTitleTextPacket).count();
	}

	private static long titleAnimations(Capture c) {
		return c.sent.stream().filter(p -> p instanceof ClientboundSetTitlesAnimationPacket).count();
	}

	private static void assertRedBold(GameTestHelper h, ClientboundSystemChatPacket packet, String what) {
		var style = packet.content().getStyle();
		h.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.RED).equals(style.getColor()), what + " is red, was " + style.getColor());
		h.assertTrue(style.isBold(), what + " is bold");
	}

	/** A raid start: ONE title (text and animation) and ONE bold red chat line, both to a player near the Heart. */
	@GameTest(environment = "villageheart-gametest:threat_msg_raid", structure = YARD, skyAccess = true, maxTicks = 400)
	public void raidStartSendsTheTitleAndTheRedChatLine(GameTestHelper h) {
		begin(h);
		Capture[] seen = new Capture[1];
		BuildingTestSupport.begin(h).thenExecute(() -> seen[0] = watcher(h)).thenExecute(() -> {
			ThreatMessages.started(h.getLevel().getServer(), ThreatWarnings.Kind.RAID);
			Capture c = seen[0];
			h.assertValueEqual(1L, titles(c), "title text packets");
			h.assertValueEqual(1L, titleAnimations(c), "title animation packets");
			var title = c.sent.stream().filter(p -> p instanceof ClientboundSetTitleTextPacket).map(p -> (ClientboundSetTitleTextPacket) p).findFirst().orElseThrow();
			h.assertValueEqual(ThreatWarnings.RAID_TITLE.key(), keyOf(title.text()), "the title's lang key");
			var lines = chat(c, ThreatWarnings.RAID_START.key());
			h.assertValueEqual(1, lines.size(), "red chat lines");
			assertRedBold(h, lines.get(0), "the raid start line");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** A siege start: ONE bold red chat line, and no title packet of any kind. */
	@GameTest(environment = "villageheart-gametest:threat_msg_siege", structure = YARD, skyAccess = true, maxTicks = 400)
	public void siegeStartSendsTheRedChatLineAndNoTitle(GameTestHelper h) {
		begin(h);
		Capture[] seen = new Capture[1];
		BuildingTestSupport.begin(h).thenExecute(() -> seen[0] = watcher(h)).thenExecute(() -> {
			ThreatMessages.started(h.getLevel().getServer(), ThreatWarnings.Kind.SIEGE);
			Capture c = seen[0];
			var lines = chat(c, ThreatWarnings.SIEGE_START.key());
			h.assertValueEqual(1, lines.size(), "red chat lines");
			assertRedBold(h, lines.get(0), "the siege start line");
			h.assertValueEqual(0L, titles(c), "title text packets");
			h.assertValueEqual(0L, titleAnimations(c), "title animation packets");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** Lead-up lines: gold, not bold, no title, one chat packet each. */
	@GameTest(environment = "villageheart-gametest:threat_msg_warn", structure = YARD, skyAccess = true, maxTicks = 400)
	public void leadUpLinesAreGoldChat(GameTestHelper h) {
		begin(h);
		Capture[] seen = new Capture[1];
		BuildingTestSupport.begin(h).thenExecute(() -> seen[0] = watcher(h)).thenExecute(() -> {
			var server = h.getLevel().getServer();
			for (ThreatWarnings.Kind kind : ThreatWarnings.Kind.values()) {
				for (int i = 0; i < kind.lines().size(); i++) ThreatMessages.warn(server, kind, i);
			}
			Capture c = seen[0];
			int total = ThreatWarnings.Kind.RAID.lines().size() + ThreatWarnings.Kind.SIEGE.lines().size();
			int gold = 0;
			for (ThreatWarnings.Kind kind : ThreatWarnings.Kind.values()) {
				for (ThreatWarnings.Line line : kind.lines()) {
					var got = chat(c, line.key());
					h.assertValueEqual(1, got.size(), "chat packets for " + line.key());
					var style = got.get(0).content().getStyle();
					h.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.GOLD).equals(style.getColor()), line.key() + " is gold, was " + style.getColor());
					h.assertTrue(!style.isBold(), line.key() + " is not bold");
					gold++;
				}
			}
			h.assertValueEqual(total, gold, "every lead-up line was checked");
			h.assertValueEqual(0L, titles(c), "a warning is never a title");
			VillageTestSupport.done(h);
			end(h);
		});
	}
}
