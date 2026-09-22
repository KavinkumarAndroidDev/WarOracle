package com.kkdev.waroracle.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ClashApiTokenProvider
{
	private static final long DEFAULT_COOLDOWN_MILLIS = 30_000L; // 30 seconds cooldown on 429

	private final List<String> tokens;
	private final AtomicInteger index = new AtomicInteger(0);
	private final ConcurrentHashMap<String, Long> rateLimitedTokens = new ConcurrentHashMap<>();

	public ClashApiTokenProvider(
			@Value("${clash.api.tokens:${clash.api.token:}}") String rawTokens)
	{
		List<String> parsedTokens = new ArrayList<>();
		if (rawTokens != null && !rawTokens.trim().isEmpty())
		{
			// Split on commas, semicolons, or newlines
			String[] split = rawTokens.split("[,;\\r\\n]+");
			for (String t : split)
			{
				String trimmed = t.trim();
				if (!trimmed.isEmpty())
				{
					parsedTokens.add(trimmed);
				}
			}
		}

		if (parsedTokens.isEmpty())
		{
			log.warn("No Clash API tokens configured in clash.api.tokens / clash.api.token. Live API requests may fail with 401 Unauthorized.");
			this.tokens = Collections.emptyList();
		}
		else
		{
			this.tokens = Collections.unmodifiableList(parsedTokens);
			log.info("Initialized ClashApiTokenProvider with {} token(s)", this.tokens.size());
		}
	}

	public String getNextToken()
	{
		if (tokens.isEmpty())
		{
			return "";
		}

		if (tokens.size() == 1)
		{
			return tokens.get(0);
		}

		long now = System.currentTimeMillis();
		// Clean up expired cooldowns
		rateLimitedTokens.entrySet().removeIf(entry -> now >= entry.getValue());

		List<String> availableTokens = new ArrayList<>(tokens.size());
		for (String candidate : tokens)
		{
			if (!rateLimitedTokens.containsKey(candidate))
			{
				availableTokens.add(candidate);
			}
		}

		if (availableTokens.isEmpty())
		{
			// All tokens are rate-limited; round-robin across full token list as fallback
			int fallbackIdx = Math.floorMod(index.getAndIncrement(), tokens.size());
			log.warn("All {} Clash API tokens are in 429 cooldown. Falling back to index {}", tokens.size(), fallbackIdx);
			return tokens.get(fallbackIdx);
		}

		int activeIdx = Math.floorMod(index.getAndIncrement(), availableTokens.size());
		return availableTokens.get(activeIdx);
	}

	public void markRateLimited(String token)
	{
		if (token == null || token.isEmpty())
		{
			return;
		}

		long cooldownExpiry = System.currentTimeMillis() + DEFAULT_COOLDOWN_MILLIS;
		rateLimitedTokens.put(token, cooldownExpiry);
		int tokenIdx = tokens.indexOf(token);
		log.warn("Clash API token index {} flagged for HTTP 429 Rate Limit. Cooldown active for {}ms",
				(tokenIdx >= 0 ? tokenIdx : "unknown"), DEFAULT_COOLDOWN_MILLIS);
	}

	public int getTokenCount()
	{
		return tokens.size();
	}

	public int getActiveTokenCount()
	{
		long now = System.currentTimeMillis();
		int active = 0;
		for (String token : tokens)
		{
			Long cooldown = rateLimitedTokens.get(token);
			if (cooldown == null || now >= cooldown)
			{
				active++;
			}
		}
		return active;
	}
}
