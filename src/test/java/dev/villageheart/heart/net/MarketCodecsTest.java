package dev.villageheart.heart.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.villageheart.core.heart.HeartAction;
import dev.villageheart.core.heart.Stock;
import dev.villageheart.core.heart.StockLine;
import dev.villageheart.core.market.Reserves;
import dev.villageheart.core.stock.StorageAlert;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;

/** M22: the wire forms of the reserve action and of the market's figures on the Stockpile tab. */
class MarketCodecsTest {
	private static <T> T roundTrip(StreamCodec<ByteBuf, T> codec, T value) {
		ByteBuf buf = Unpooled.buffer();
		codec.encode(buf, value);
		return codec.decode(buf);
	}

	@Test void setReservesPayloadRoundTripsAndEveryIntDecodes() {
		for (int[] v : new int[][] {{3, 64}, {0, 0}, {30, 1024}, {-1, 5}, {99, Integer.MAX_VALUE}, {Integer.MIN_VALUE, -7}}) {
			SetReservesPayload p = new SetReservesPayload(4, v[0], v[1]);
			assertEquals(p, roundTrip(SetReservesPayload.CODEC, p));
			assertEquals(new HeartAction.SetReserves(v[0], v[1]), p.action());
		}
	}

	@Test void stockWithTheMarketFiguresRoundTrips() {
		Stock s = new Stock(List.of(new StockLine("minecraft:cobblestone", 200, 0, 136), new StockLine("minecraft:wheat", 30, 0, 0)), 0, 230, 0, 1, 0, 2,
				40, 54, StorageAlert.OK, new Reserves(2, 80), 7, 2);
		assertEquals(s, roundTrip(HeartCodecs.STOCK, s));
	}

	@Test void aHostileStockCannotMakeTheClientThrow() {
		ByteBuf hostile = Unpooled.buffer();
		HeartCodecs.STOCK.encode(hostile, new Stock(List.of(new StockLine("minecraft:bread", 1, 5, Long.MIN_VALUE)), 0, 0, 0, 0, 0, 0, 0, 0, StorageAlert.OK));
		Stock back = HeartCodecs.STOCK.decode(hostile);
		assertEquals(0, back.items().get(0).forSale(), "a negative figure reads as 0");
		assertEquals(Reserves.clamped(99, -5), new Reserves(30, 0));
	}
}
