package com.arzban.app;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PriceRepositoryLiveTest {
    @Test
    public void parsesCoreRatesFromPublicChannel() throws Exception {
        TelegramMarketResult result = PriceRepository.fetchTelegramMarket();

        assertNotNull(result);
        assertNotNull(result.snapshot);

        AssetQuote dollar = result.snapshot.quotes.get(Asset.DOLLAR);
        AssetQuote gold = result.snapshot.quotes.get(Asset.GOLD18);
        AssetQuote coin = result.snapshot.quotes.get(Asset.COIN_EMAMI);

        assertNotNull("Dollar must be parsed", dollar);
        assertNotNull("18k gold must be parsed", gold);
        assertNotNull("Emami coin must be parsed", coin);

        assertTrue("Dollar value must be plausible", dollar.value > 10_000d);
        assertTrue("Gold value must be plausible", gold.value > 1_000_000d);
        assertTrue("Coin value must be plausible", coin.value > 10_000_000d);
    }
}
