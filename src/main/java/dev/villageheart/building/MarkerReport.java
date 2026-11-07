package dev.villageheart.building;

import dev.villageheart.core.building.BuildingKind;
import dev.villageheart.core.building.BuildingRequirements;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.building.Reason;
import dev.villageheart.core.building.Requirements.DetailLine;
import dev.villageheart.core.building.Verdict;
import dev.villageheart.core.room.Pos;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The chat report of a right-click. Everything is {@code translatableWithFallback}: the English text is the fallback, so no lang
 * entry is needed on the server and a later translation needs no code change.
 */
public final class MarkerReport {
	private MarkerReport() {}

	public static final String HEADER_KEY = "message.villageheart.marker.";

	public static Component noVillage() {
		return reason(Reason.noVillage()).withStyle(ChatFormatting.RED);
	}

	/** One line per message: header, one line per reason, the detail line, the link. */
	public static List<Component> lines(BuildingType type, Pos pos, Verdict verdict) {
		List<Component> out = new ArrayList<>();
		String state = verdict.deferred() ? "deferred" : verdict.status().name().toLowerCase(java.util.Locale.ROOT);
		String word = switch (state) {
			case "valid" -> "valid";
			case "invalid" -> "invalid";
			case "deferred" -> "cannot check now";
			default -> "not checked yet";
		};
		ChatFormatting colour = switch (verdict.status()) {
			case VALID -> ChatFormatting.GREEN;
			case INVALID -> ChatFormatting.RED;
			case UNCHECKED -> ChatFormatting.GRAY;
		};
		out.add(Component.translatableWithFallback(HEADER_KEY + state, "%s at %s: " + word, type.displayName(), pos.toString())
				.withStyle(colour));
		for (Reason r : verdict.reasons()) out.add(Component.literal(" - ").append(reason(r)).withStyle(ChatFormatting.WHITE));
		if (type.kind() == BuildingKind.ROOM && !verdict.deferred()) {
			DetailLine detail = BuildingRequirements.of(type).detail();
			String key = switch (detail) {
				case WORKER_SLOTS -> "slots";
				case HOMES -> "homes";
				case CONTAINERS -> "containers";
			};
			String label = switch (detail) {
				case WORKER_SLOTS -> "Worker slots";
				case HOMES -> "Homes";
				case CONTAINERS -> "Containers";
			};
			out.add(Component.translatableWithFallback(HEADER_KEY + key, label + ": %s", verdict.slots()).withStyle(ChatFormatting.GRAY));
		}
		if (verdict.link() != null && type.parent().isPresent()) {
			out.add(Component.translatableWithFallback(HEADER_KEY + "linked", "Linked to %s at %s",
					type.parent().get().displayName(), verdict.link().toString()).withStyle(ChatFormatting.GRAY));
		}
		return out;
	}

	public static MutableComponent reason(Reason r) {
		return Component.translatableWithFallback(r.translationKey(), r.message());
	}
}
