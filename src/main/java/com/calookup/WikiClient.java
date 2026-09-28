package com.calookup;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * The plugin's only network access: read-only calls to the Old School RuneScape Wiki.
 * <ul>
 *   <li>The global completion rate of every task, one JSON page the wiki maintains, together
 *       with the timestamp of the revision so the panel can say how old the numbers are.</li>
 *   <li>The plain-text extract of one task's page, fetched when the player opens that task.</li>
 *   <li>The lead image of a boss's page, for the portrait in the panel header.</li>
 * </ul>
 * Requests use RuneLite's shared {@link OkHttpClient} and run asynchronously. Callbacks are
 * invoked on an OkHttp thread; callers must hop to the Swing thread themselves.
 */
@Slf4j
@Singleton
class WikiClient
{
	static final String WIKI_HOST = "oldschool.runescape.wiki";
	static final String USER_AGENT = "Combat Achievement Lookup RuneLite plugin (https://github.com/jimmyununu/combat-achievement-lookup)";
	static final String COMPLETION_PAGE = "Module:Combat Achievements/completion.json";

	private final OkHttpClient okHttpClient;
	private final Gson gson;

	@Inject
	WikiClient(OkHttpClient okHttpClient, Gson gson)
	{
		this.okHttpClient = okHttpClient;
		this.gson = gson;
	}

	private static HttpUrl.Builder api()
	{
		return new HttpUrl.Builder()
			.scheme("https")
			.host(WIKI_HOST)
			.addPathSegment("api.php")
			.addQueryParameter("format", "json")
			.addQueryParameter("formatversion", "2");
	}

	/** @return the wiki page for a task, e.g. {@code https://oldschool.runescape.wiki/w/Noxious_Foe} */
	static HttpUrl taskPageUrl(String taskName)
	{
		return new HttpUrl.Builder()
			.scheme("https")
			.host(WIKI_HOST)
			.addPathSegment("w")
			.addPathSegment(taskName.replace(' ', '_'))
			.addQueryParameter("utm_source", "runelite-ca-lookup")
			.build();
	}

	/** Completion data plus the timestamp of its latest revision, in one request. */
	static HttpUrl completionUrl()
	{
		return api()
			.addQueryParameter("action", "query")
			.addQueryParameter("prop", "revisions")
			.addQueryParameter("rvprop", "timestamp|content")
			.addQueryParameter("rvslots", "main")
			.addQueryParameter("titles", COMPLETION_PAGE)
			.build();
	}

	static HttpUrl extractUrl(String taskName)
	{
		return api()
			.addQueryParameter("action", "query")
			.addQueryParameter("prop", "extracts")
			.addQueryParameter("explaintext", "1")
			.addQueryParameter("exsectionformat", "wiki")
			.addQueryParameter("redirects", "1")
			.addQueryParameter("titles", taskName)
			.build();
	}

	static HttpUrl pageImageUrl(String title, int size)
	{
		return api()
			.addQueryParameter("action", "query")
			.addQueryParameter("prop", "pageimages")
			.addQueryParameter("pithumbsize", Integer.toString(size))
			.addQueryParameter("redirects", "1")
			.addQueryParameter("titles", title)
			.build();
	}

	private Request request(HttpUrl url)
	{
		return new Request.Builder()
			.url(url)
			.header("User-Agent", USER_AGENT)
			.get()
			.build();
	}

	/**
	 * Fetches the wiki's completion rate for every task.
	 *
	 * @param onSuccess receives a map of task ID to percentage, and the revision timestamp (may be null)
	 * @param onError   receives a short human-readable reason
	 */
	void fetchCompletionRates(BiConsumer<Map<Integer, Double>, Instant> onSuccess, Consumer<String> onError)
	{
		okHttpClient.newCall(request(completionUrl())).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Completion rate request failed", e);
				onError.accept("Could not reach the wiki");
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					if (!response.isSuccessful() || body == null)
					{
						onError.accept("Wiki answered with HTTP " + response.code());
						return;
					}
					JsonObject revision = latestRevision(gson, body.string());
					if (revision == null)
					{
						onError.accept("Wiki completion data was empty");
						return;
					}
					String content = revision.getAsJsonObject("slots").getAsJsonObject("main").get("content").getAsString();
					Map<Integer, Double> rates = parseRates(gson, new StringReader(content));
					if (rates.isEmpty())
					{
						onError.accept("Wiki completion data was empty");
						return;
					}
					onSuccess.accept(rates, parseTimestamp(revision));
				}
				catch (IOException | JsonSyntaxException | JsonIOException | IllegalStateException | NullPointerException e)
				{
					log.debug("Completion rate response could not be read", e);
					onError.accept("Wiki completion data could not be read");
				}
			}
		});
	}

	/** @return the first page's latest revision object, or null. Package-private for tests. */
	static JsonObject latestRevision(Gson gson, String json)
	{
		JsonObject root = gson.fromJson(json, JsonObject.class);
		if (root == null || !root.has("query"))
		{
			return null;
		}
		JsonElement pages = root.getAsJsonObject("query").get("pages");
		if (pages == null || !pages.isJsonArray() || pages.getAsJsonArray().size() == 0)
		{
			return null;
		}
		JsonObject page = pages.getAsJsonArray().get(0).getAsJsonObject();
		JsonElement revisions = page.get("revisions");
		if (revisions == null || !revisions.isJsonArray() || revisions.getAsJsonArray().size() == 0)
		{
			return null;
		}
		return revisions.getAsJsonArray().get(0).getAsJsonObject();
	}

	static Instant parseTimestamp(JsonObject revision)
	{
		JsonElement ts = revision.get("timestamp");
		if (ts == null || ts.isJsonNull())
		{
			return null;
		}
		try
		{
			return Instant.parse(ts.getAsString());
		}
		catch (DateTimeParseException e)
		{
			return null;
		}
	}

	/** Parses the {@code {"0": 69.9, "1": 31.3, ...}} document. Package-private for tests. */
	static Map<Integer, Double> parseRates(Gson gson, java.io.Reader reader)
	{
		JsonObject raw = gson.fromJson(reader, JsonObject.class);
		Map<Integer, Double> rates = new HashMap<>();
		if (raw == null)
		{
			return rates;
		}
		for (Map.Entry<String, JsonElement> entry : raw.entrySet())
		{
			JsonElement value = entry.getValue();
			if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
			{
				continue;
			}
			try
			{
				rates.put(Integer.parseInt(entry.getKey().trim()), value.getAsDouble());
			}
			catch (NumberFormatException ignored)
			{
				// Not a task id; skip.
			}
		}
		return rates;
	}

	/**
	 * Fetches the plain-text extract of a task's wiki page.
	 *
	 * @param taskName  the task title, which is also its wiki page title
	 * @param onSuccess receives the parsed summary
	 * @param onError   receives a short human-readable reason
	 */
	void fetchTaskSummary(String taskName, Consumer<WikiSummary> onSuccess, Consumer<String> onError)
	{
		okHttpClient.newCall(request(extractUrl(taskName))).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Extract request failed for {}", taskName, e);
				onError.accept("Could not reach the wiki");
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					if (!response.isSuccessful() || body == null)
					{
						onError.accept("Wiki answered with HTTP " + response.code());
						return;
					}
					String extract = parseExtract(gson, body.string());
					if (extract == null)
					{
						onError.accept("The wiki has no page for this task yet");
						return;
					}
					onSuccess.accept(WikiSummary.parse(extract));
				}
				catch (IOException | JsonSyntaxException | IllegalStateException e)
				{
					log.debug("Extract response could not be read for {}", taskName, e);
					onError.accept("Wiki page could not be read");
				}
			}
		});
	}

	/**
	 * Pulls the extract text out of a TextExtracts response, or null if the page is missing.
	 * Package-private for tests.
	 */
	static String parseExtract(Gson gson, String json)
	{
		JsonObject page = firstPage(gson, json);
		if (page == null || (page.has("missing") && page.get("missing").getAsBoolean()))
		{
			return null;
		}
		JsonElement extract = page.get("extract");
		return extract == null || extract.isJsonNull() ? "" : extract.getAsString();
	}

	/** @return the thumbnail URL from a pageimages response, or null. Package-private for tests. */
	static String parseThumbnail(Gson gson, String json)
	{
		JsonObject page = firstPage(gson, json);
		if (page == null || !page.has("thumbnail"))
		{
			return null;
		}
		JsonElement source = page.getAsJsonObject("thumbnail").get("source");
		return source == null || source.isJsonNull() ? null : source.getAsString();
	}

	private static JsonObject firstPage(Gson gson, String json)
	{
		JsonObject root = gson.fromJson(json, JsonObject.class);
		if (root == null || !root.has("query"))
		{
			return null;
		}
		JsonElement pagesEl = root.getAsJsonObject("query").get("pages");
		if (pagesEl == null || !pagesEl.isJsonArray())
		{
			return null;
		}
		JsonArray pages = pagesEl.getAsJsonArray();
		return pages.size() == 0 ? null : pages.get(0).getAsJsonObject();
	}

	/**
	 * Fetches the lead image of a boss's wiki page. A mode page without an image of its own
	 * (for example "Theatre of Blood: Hard Mode") falls back to its parent page.
	 *
	 * @param boss      the boss group name as the game files it
	 * @param size      thumbnail width in pixels
	 * @param onSuccess receives the decoded image
	 * @param onError   receives a short human-readable reason
	 */
	void fetchPortrait(String boss, int size, Consumer<BufferedImage> onSuccess, Consumer<String> onError)
	{
		fetchThumbnailUrl(boss, size, url ->
		{
			if (url != null)
			{
				fetchImage(url, onSuccess, onError);
				return;
			}
			int colon = boss.indexOf(':');
			if (colon > 0)
			{
				fetchThumbnailUrl(boss.substring(0, colon).trim(), size, parentUrl ->
				{
					if (parentUrl == null)
					{
						onError.accept("No image");
					}
					else
					{
						fetchImage(parentUrl, onSuccess, onError);
					}
				}, onError);
			}
			else
			{
				onError.accept("No image");
			}
		}, onError);
	}

	private void fetchThumbnailUrl(String title, int size, Consumer<String> onResult, Consumer<String> onError)
	{
		okHttpClient.newCall(request(pageImageUrl(title, size))).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Page image request failed for {}", title, e);
				onError.accept("Could not reach the wiki");
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					if (!response.isSuccessful() || body == null)
					{
						onError.accept("Wiki answered with HTTP " + response.code());
						return;
					}
					onResult.accept(parseThumbnail(gson, body.string()));
				}
				catch (IOException | JsonSyntaxException | IllegalStateException e)
				{
					log.debug("Page image response could not be read for {}", title, e);
					onError.accept("Wiki image could not be read");
				}
			}
		});
	}

	private void fetchImage(String url, Consumer<BufferedImage> onSuccess, Consumer<String> onError)
	{
		HttpUrl parsed = HttpUrl.parse(url);
		if (parsed == null || !WIKI_HOST.equals(parsed.host()))
		{
			onError.accept("Unexpected image host");
			return;
		}
		okHttpClient.newCall(request(parsed)).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Image request failed for {}", url, e);
				onError.accept("Could not reach the wiki");
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					if (!response.isSuccessful() || body == null)
					{
						onError.accept("Wiki answered with HTTP " + response.code());
						return;
					}
					BufferedImage image = ImageIO.read(body.byteStream());
					if (image == null)
					{
						onError.accept("Image could not be decoded");
						return;
					}
					onSuccess.accept(image);
				}
				catch (IOException e)
				{
					log.debug("Image could not be read from {}", url, e);
					onError.accept("Wiki image could not be read");
				}
			}
		});
	}
}
