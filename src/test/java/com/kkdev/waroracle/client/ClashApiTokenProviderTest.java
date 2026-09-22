package com.kkdev.waroracle.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class ClashApiTokenProviderTest
{
	@Test
	void testSingleToken()
	{
		ClashApiTokenProvider provider = new ClashApiTokenProvider("token-alpha");
		assertEquals(1, provider.getTokenCount());
		assertEquals("token-alpha", provider.getNextToken());
		assertEquals("token-alpha", provider.getNextToken());
	}

	@Test
	void testMultipleTokensRoundRobin()
	{
		ClashApiTokenProvider provider = new ClashApiTokenProvider("token-1, token-2, token-3");
		assertEquals(3, provider.getTokenCount());

		assertEquals("token-1", provider.getNextToken());
		assertEquals("token-2", provider.getNextToken());
		assertEquals("token-3", provider.getNextToken());
		assertEquals("token-1", provider.getNextToken());
	}

	@Test
	void testCooldownFailoverOn429()
	{
		ClashApiTokenProvider provider = new ClashApiTokenProvider("token-1, token-2, token-3");

		// Simulate token-1 hitting 429
		provider.markRateLimited("token-1");

		// Active tokens should now be 2
		assertEquals(2, provider.getActiveTokenCount());

		// Next tokens should skip token-1
		String next1 = provider.getNextToken();
		String next2 = provider.getNextToken();

		assertEquals("token-2", next1);
		assertEquals("token-3", next2);
	}

	@Test
	void testAllTokensRateLimitedFallback()
	{
		ClashApiTokenProvider provider = new ClashApiTokenProvider("token-1, token-2");
		provider.markRateLimited("token-1");
		provider.markRateLimited("token-2");

		assertEquals(0, provider.getActiveTokenCount());

		// Should not crash, falls back gracefully
		String token = provider.getNextToken();
		assertNotNull(token);
	}
}
