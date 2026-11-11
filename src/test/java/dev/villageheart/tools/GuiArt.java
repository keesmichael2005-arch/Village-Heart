package dev.villageheart.tools;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GUI sprites. Small ones are 8x8 and drawn at 8 so they stay crisp beside 9px text. Need icons are 16x16,
 * the same size as the item the Heart screen used to render in that slot. Paths match
 * {@code dev.villageheart.client.art.ModTextures}.
 */
public final class GuiArt {
	private GuiArt() {}

	public static final String ROOT = "textures/gui/sprites/";

	public static Map<String, BufferedImage> sprites() {
		Map<String, BufferedImage> m = new LinkedHashMap<>();
		// tabs
		put8(m, "heart/tab_overview",
				"........",
				".RR..RR.",
				"RrrRRrrR",
				"RrrrrrrR",
				".Rrrrrk.",
				"..Rrrk..",
				"...Rk...",
				"........");
		put8(m, "heart/tab_villagers",
				".oooooo.",
				".occcco.",
				".ociico.",
				".occcco.",
				".ocooCo.",
				".occcco.",
				".oooooo.",
				"........");
		put8(m, "heart/tab_buildings",
				"...RR...",
				"..RRRR..",
				".RRRRRR.",
				"oCCCCCCo",
				".oCRRCo.",
				".oCRRCo.",
				"..oooo..",
				"........");
		put8(m, "heart/tab_stockpile",
				".oGGGGo.",
				".oGGGGGo",
				"oooooooo",
				".oCCCCo.",
				".oCaCCo.",
				".oCCCCo.",
				".oooooo.",
				"........");
		put8(m, "heart/tab_ranks",
				"g.g.g.g.",
				"GgGgGgGg",
				".GGGGGG.",
				"..GGGG..",
				"...GG...",
				"...MM...",
				"........",
				"........");
		// tiers
		put8(m, "heart/tier_hamlet",
				"........",
				"...RR...",
				"..RRRR..",
				".oCCCCo.",
				".oCRRCo.",
				".oCRRCo.",
				"..oooo..",
				"........");
		put8(m, "heart/tier_village",
				"..cccc..",
				"..oooo..",
				".oggggo.",
				".oGGGGo.",
				".oGGGGo.",
				".oMMMMo.",
				"..oooo..",
				"........");
		put8(m, "heart/tier_town",
				"R.RR.RR.",
				"RRRRRRRR",
				"oCCoCCoC",
				".oC.C.C.",
				".oC.C.C.",
				"..o...o.",
				"........",
				"........");
		// ranks
		put8(m, "heart/rank_owner",
				"g.g.g.g.",
				"GgGgGgGg",
				".GGGGGG.",
				"..GrrG..",
				"...RR...",
				"...MM...",
				"........",
				"........");
		put8(m, "heart/rank_steward",
				"..oooo..",
				"..oggGo.",
				"..oGGo..",
				"...oo...",
				"...GG...",
				"...GG...",
				"..GGGG..",
				"........");
		put8(m, "heart/rank_resident",
				".oooooo.",
				".occcco.",
				".ocCCco.",
				".occcco.",
				".occgco.",
				".occcco.",
				".oooooo.",
				"........");
		put8(m, "heart/rank_visitor",
				".oooooo.",
				".oIIIIo.",
				".oI..Io.",
				".oIIIIo.",
				"..oIIo..",
				"...oo...",
				"........",
				"........");
		// goals, alerts, status
		put8(m, "heart/goal_met",
				"........",
				"......e.",
				".....ee.",
				"e...ee..",
				"ee.ee...",
				".eee....",
				"..e.....",
				"........");
		put8(m, "heart/goal_open",
				"........",
				".tttttt.",
				".t....t.",
				".t....t.",
				".t....t.",
				".t....t.",
				".tttttt.",
				"........");
		put8(m, "heart/alert_danger",
				"..oRRo..",
				"..oRRo..",
				"..oRRo..",
				"..oRRo..",
				"........",
				"..oRRo..",
				"..oRRo..",
				"........");
		put8(m, "heart/alert_warning",
				"..oGGo..",
				"..oGGo..",
				"..oGGo..",
				"..oGGo..",
				"........",
				"..oGGo..",
				"..oGGo..",
				"........");
		put8(m, "heart/status_valid",
				"........",
				"......E.",
				".....EE.",
				"E...EE..",
				"EE.EE...",
				".EEE....",
				"..E.....",
				"........");
		put8(m, "heart/status_invalid",
				".R....R.",
				"..R..R..",
				"...RR...",
				"...RR...",
				"..R..R..",
				".R....R.",
				"........",
				"........");
		put8(m, "heart/status_unchecked",
				".tttttt.",
				".t....t.",
				".t....t.",
				".t.tt.t.",
				".t....t.",
				".t....t.",
				".tttttt.",
				"........");
		put8(m, "heart/status_progress",
				".iiiiii.",
				".iIIIIi.",
				"...CC...",
				"...CC...",
				"...CC...",
				"...CC...",
				"...aa...",
				"........");
		put8(m, "heart/status_waiting",
				".oooooo.",
				"..oGGo..",
				"...oo...",
				"...gg...",
				"..oGGo..",
				".oGGGGo.",
				".oooooo.",
				"........");
		put8(m, "heart/status_blocked",
				".oRRRRo.",
				".RRRRRR.",
				".RRRRoR.",
				".RRoRRR.",
				".RoRRRR.",
				".RRRRRR.",
				".oRRRRo.",
				"........");
		// jobs. The same silhouette as the villager's badge and the building icon.
		put8(m, "heart/job_farmer", wheat());
		put8(m, "heart/job_lumberjack", axe());
		put8(m, "heart/job_cook", pot());
		put8(m, "heart/job_builder", hammer());
		put8(m, "heart/job_miner", pick());
		put8(m, "heart/job_hunter", bow());
		put8(m, "heart/job_guard", shield());
		put8(m, "heart/job_merchant", gem());
		put8(m, "heart/job_blacksmith", anvil());
		// buildings
		put8(m, "heart/building_house", new String[] {
				"...RR...", "..RRRR..", ".RRRRRR.", "oCCCCCCo", ".oCRRCo.", ".oCRRCo.", "..oooo..", "........"});
		put8(m, "heart/building_storage", new String[] {
				".oGGGGo.", ".oGGGGGo", "oooooooo", ".oCCCCo.", ".oCaCCo.", ".oCCCCo.", ".oooooo.", "........"});
		put8(m, "heart/building_farmhouse", wheat());
		put8(m, "heart/building_lumber_camp", axe());
		put8(m, "heart/building_kitchen", pot());
		put8(m, "heart/building_builders_hut", hammer());
		put8(m, "heart/building_mine", pick());
		put8(m, "heart/building_hunting_lodge", bow());
		put8(m, "heart/building_barracks", shield());
		put8(m, "heart/building_market", gem());
		put8(m, "heart/building_smithy", anvil());
		put8(m, "heart/building_mineshaft", new String[] {
				"t.t.t.t.", "tttttttt", ".cCcCcCc", "tttttttt", "t.t.t.t.", "tttttttt", ".cCcCcCc", "........"});
		put8(m, "heart/building_hunting_ground", new String[] {
				"P.P.P...", "oPoPoPo.", ".oPoPo..", "..oPo...", "...o....", "........", "........", "........"});
		put8(m, "heart/building_training_yard", new String[] {
				".oRRRRo.", ".oRGGRo.", ".oRGGRo.", ".oRRRRo.", "..oooo..", "...CC...", "...CC...", "........"});
		// table
		put8(m, "table/blueprint", new String[] {
				".oooooo.", ".ollllo.", ".ollolo.", ".ollllo.", ".ollllo.", ".obbbbo.", ".oooooo.", "........"});
		put8(m, "table/locked", new String[] {
				"..oooo..", "..o..o..", "..o..o..", ".oooooo.", ".oGGGGo.", ".oGGGGo.", ".oooooo.", "........"});
		put8(m, "table/materials", new String[] {
				".oCCCCo.", ".oCCCCo.", "oooooooo", ".oCaCCo.", ".oCCCCo.", ".oooooo.", "........", "........"});
		// needs, drawn at the 16px item slot
		m.put(ROOT + "heart/need_food.png", loaf());
		m.put(ROOT + "heart/need_bed.png", bed());
		m.put(ROOT + "heart/need_pay.png", pay());
		// not referenced on this branch: activity badges for m19 and m25, ready to blit when those lines merge
		put8(m, "heart/activity_smithing", anvil());
		put8(m, "heart/activity_golem", new String[] {
				".oooooo.", ".oiiiio.", ".oiRRio.", ".oiiiio.", ".oiiiio.", ".oooooo.", ".oIIIIo.", ".oooooo."});
		put8(m, "heart/activity_shelter", new String[] {
				"...CC...", "..CCCC..", ".CCCCCC.", "CCCCCCCC", ".oPPPPo.", ".oPPPPo.", "..oooo..", "........"});
		put8(m, "heart/activity_no_shelter", new String[] {
				"...CC...", "..CCCC..", ".CCRCCC.", "CCRRRCCC", ".oRPRPo.", ".oRRRPo.", "..oooo..", "........"});
		for (Map.Entry<String, BufferedImage> e : m.entrySet()) {
			String name = e.getKey();
			BufferedImage img = e.getValue();
			if (img.getWidth() != img.getHeight()) throw new IllegalStateException(name + " not square");
		}
		return m;
	}

	private static void put8(Map<String, BufferedImage> m, String path, String[] rows) {
		if (rows.length != 8) throw new IllegalStateException(path);
		for (String row : rows) if (row.length() != 8) throw new IllegalStateException(path + " '" + row + "'");
		m.put(ROOT + path + ".png", ArtKit.sprite(rows));
	}

	private static String[] wheat() {
		return new String[] {"..p..p..", ".pYp.pY.", "..YYYY..", ".YYYYY..", "..YYY...", "...Y....", "...Y....", "........"};
	}

	private static String[] axe() {
		return new String[] {"....iii.", "...iIIs.", "..oIs...", ".oIs....", "oCs.....", "Cs......", "a.......", "........"};
	}

	private static String[] pot() {
		return new String[] {"w..w..w.", ".w.w.w..", ".oooooo.", ".oRRRRo.", ".oRRRRo.", ".oooooo.", "..o..o..", "........"};
	}

	private static String[] hammer() {
		return new String[] {".iiiiii.", ".iIIIIi.", "...CC...", "...CC...", "...CC...", "...CC...", "...aa...", "........"};
	}

	private static String[] pick() {
		return new String[] {"iii.....", "iIsi....", ".oI.....", "..oC....", "...oC...", "....oC..", ".....a..", "........"};
	}

	private static String[] bow() {
		return new String[] {".o....Y.", "o..o..Y.", "o...YYYY", "o..o..Y.", ".o....Y.", "........", "........", "........"};
	}

	private static String[] shield() {
		return new String[] {".oooooo.", ".oBBBBo.", ".oBrRBo.", ".oBRRBo.", ".oBBBBo.", "..oBBo..", "...oo...", "........"};
	}

	private static String[] gem() {
		return new String[] {"...ee...", "..eEEe..", ".eEEEEe.", ".eEEddE.", "..eEde..", "...ee...", "........", "........"};
	}

	private static String[] anvil() {
		return new String[] {".ssssss.", ".sIIIIs.", "..sIIs..", "..sIIs..", ".ssssss.", "ssssssss", "........", "........"};
	}

	private static BufferedImage loaf() {
		return ArtKit.icon16(
				"................",
				"................",
				"......oooo......",
				"....ooyyyyo.....",
				"...oyYYYYYyo....",
				"..oyYYYYYYYYo...",
				"..oYYYYYYYYYo...",
				"..oYYYYoYYYYo...",
				"..oYYYYYYYYYo...",
				"..oyYYYYYYYYo...",
				"...oyYYYYYyo....",
				"....ooyyyyo.....",
				"......oooo......",
				"................",
				"................",
				"................");
	}

	private static BufferedImage bed() {
		return ArtKit.icon16(
				"................",
				"................",
				"...oooooooooo...",
				"..owwwwwRRRRRo..",
				"..owwwwwRRRRRo..",
				".owwwwwwRRRRRRo.",
				".oCCCCCCCCCCCCo.",
				".oCaCCCCCCCCaCo.",
				".oCCCCCCCCCCCCo.",
				"..o..........o..",
				"..oo........oo..",
				"................",
				"................",
				"................",
				"................",
				"................");
	}

	private static BufferedImage pay() {
		return ArtKit.icon16(
				"................",
				".......oo.......",
				"......oeeo......",
				".....oeEEeo.....",
				"....oeEEEEeo....",
				"...oeEEEEEEeo...",
				"...oeEEEEddo....",
				"....oeEEddo.....",
				".....oeEdo......",
				"......oeo.......",
				".......o........",
				"......g.........",
				"................",
				"................",
				"................",
				"................");
	}
}
