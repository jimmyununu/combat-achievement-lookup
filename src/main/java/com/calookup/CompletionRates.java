package com.calookup;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * The share of players who have completed each task, keyed by task ID.
 * <p>
 * The plugin ships a snapshot of the wiki's numbers so the panel is useful offline or when the
 * wiki is off-limits, and replaces it with the live document when that can be fetched. Each
 * copy knows when the wiki last regenerated the data, which is what the footer reports.
 */
@Slf4j
final class CompletionRates
{
	enum Source
	{
		/** Nothing loaded at all. */
		NONE,
		/** The copy bundled with the plugin. */
		SNAPSHOT,
		/** Fetched from the wiki during this session. */
		WIKI
	}

	static final String SNAPSHOT_RESOURCE = "completion-snapshot.json";
	/** The wiki revision the bundled snapshot was taken from. */
	static final Instant SNAPSHOT_DATA_DATE = Instant.parse("2026-08-31T00:44:46Z");

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy");

	static final CompletionRates EMPTY = new CompletionRates(Collections.emptyMap(), Source.NONE, null, null);

	private final Map<Integer, Double> rates;
	private final Source source;
	private final Instant fetchedAt;
	private final Instant dataDate;

	CompletionRates(Map<Integer, Double> rates, Source source, Instant fetchedAt, Instant dataDate)
	{
		this.rates = Collections.unmodifiableMap(rates);
		this.source = source;
		this.fetchedAt = fetchedAt;
		this.dataDate = dataDate;
	}

	/** Loads the bundled snapshot through {@code getResourceAsStream}, as the plugin hub requires. */
	static CompletionRates loadSnapshot(Gson gson)
	{
		try (InputStream in = CompletionRates.class.getResourceAsStream(SNAPSHOT_RESOURCE))
		{
			if (in == null)
			{
				log.warn("Bundled completion snapshot is missing");
				return EMPTY;
			}
			Map<Integer, Double> parsed = WikiClient.parseRates(gson, new InputStreamReader(in, StandardCharsets.UTF_8));
			return new CompletionRates(parsed, Source.SNAPSHOT, null, SNAPSHOT_DATA_DATE);
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Bundled completion snapshot could not be read", e);
			return EMPTY;
		}
	}

	static CompletionRates fromWiki(Map<Integer, Double> rates, Instant dataDate)
	{
		return new CompletionRates(rates, Source.WIKI, Instant.now(), dataDate);
	}

	/** @return the percentage of players who completed the task, or null if unknown */
	Double rateFor(int taskId)
	{
		return rates.get(taskId);
	}

	int size()
	{
		return rates.size();
	}

	Source getSource()
	{
		return source;
	}

	/** When this copy was downloaded, or null for the bundled snapshot. */
	Instant getFetchedAt()
	{
		return fetchedAt;
	}

	/** When the wiki last regenerated the numbers, or null if unknown. */
	Instant getDataDate()
	{
		return dataDate;
	}

	/** The data date as "31 Aug 2026", or null. */
	String getDataDateText()
	{
		// The wiki stamps revisions in UTC; keep the same calendar day it shows.
		return dataDate == null ? null : DATE.format(dataDate.atZone(ZoneOffset.UTC));
	}
}
