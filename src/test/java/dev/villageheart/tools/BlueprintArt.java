package dev.villageheart.tools;

import java.awt.image.BufferedImage;

/** The Blueprint item: a folded blue sheet, a white plan of a house, and a brass pin from the bell icon. */
public final class BlueprintArt {
	private BlueprintArt() {}

	public static BufferedImage render() {
		return ArtKit.icon16(
				"................",
				"..oooooooooooo..",
				"..olllllllllgo..",
				"..ollllllllloo..",
				"..oll.ll.llloo..",
				"..ollllllllooo..",
				"..ollloooo.loo..",
				"..olllolll.loo..",
				"..olllolll.loo..",
				"..olllolll.loo..",
				"..ollloooo.loo..",
				"..ollllllllllo..",
				"..obbbbbbbbbbo..",
				"..oooooooooooo..",
				"................",
				"................");
	}
}
