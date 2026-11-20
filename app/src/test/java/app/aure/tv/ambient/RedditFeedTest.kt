package app.aure.tv.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditFeedTest {

    private val feed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <entry>
            <id>t3_one</id>
            <title>French Alps &amp; Sky</title>
            <author><name>/u/alice</name></author>
            <link rel="alternate" href="https://www.reddit.com/r/TVWallpapersSFW/comments/one/"/>
            <content type="html">&lt;table&gt;&lt;a href="https://i.redd.it/one.png"&gt;&lt;img src="https://preview.redd.it/one.png?width=640&amp;amp;crop=smart"/&gt;&lt;/a&gt;&lt;/table&gt;</content>
          </entry>
          <entry>
            <id>t3_bad</id>
            <title>Wrong host</title>
            <content type="html">&lt;img src="https://evil.example/two.png"/&gt;</content>
          </entry>
          <entry>
            <id>t3_three</id>
            <title>No direct link</title>
            <author><name>/u/bob</name></author>
            <content type="html">&lt;img src="https://preview.redd.it/three.png?width=640"/&gt;</content>
          </entry>
        </feed>
    """.trimIndent()

    @Test
    fun the_parser_extracts_only_reddit_image_hosts() {
        val items = RedditFeedParser.parse(feed, "TVWallpapersSFW")

        assertEquals(2, items.size)
        assertEquals("t3_one", items[0].id)
        assertEquals("French Alps & Sky", items[0].title)
        assertEquals("alice", items[0].author)
        assertEquals("https://i.redd.it/one.png", items[0].imageUrl)
        assertTrue(items[0].previewUrl.startsWith("https://preview.redd.it/one.png"))
        assertEquals("TVWallpapersSFW", items[0].community)
        assertEquals("https://www.reddit.com/r/TVWallpapersSFW/comments/one/", items[0].postUrl)
        assertEquals("t3_three", items[1].id)
        assertEquals("https://preview.redd.it/three.png?width=640", items[1].imageUrl)
    }

    @Test
    fun an_empty_or_broken_feed_is_an_empty_list() {
        assertTrue(RedditFeedParser.parse("", "TVWallpapersSFW").isEmpty())
        assertTrue(RedditFeedParser.parse("<not-xml", "TVWallpapersSFW").isEmpty())
    }

    @Test
    fun image_urls_must_be_https_on_a_reddit_image_host() {
        assertNull(RedditFeedParser.safeImageUrl("http://i.redd.it/a.png"))
        assertNull(RedditFeedParser.safeImageUrl("https://i.redd.it.evil.example/a.png"))
        assertNull(RedditFeedParser.safeImageUrl("https://i.redd.it:8443/a.png"))
        assertNull(RedditFeedParser.safeImageUrl("https://user@i.redd.it/a.png"))
        assertEquals(
            "https://i.redd.it/a.png",
            RedditFeedParser.safeImageUrl("https://i.redd.it/a.png"),
        )
        assertEquals(
            "https://preview.redd.it/a.png?width=640&crop=smart",
            RedditFeedParser.safeImageUrl(
                "https://preview.redd.it/a.png?width=640&amp;crop=smart",
            ),
        )
    }

    @Test
    fun subreddit_input_accepts_names_and_reddit_urls_only() {
        assertEquals("TVWallpapersSFW", AmbientRedditStore.normalizeSubreddit("TVWallpapersSFW"))
        // Any public subreddit is a valid source; there is no built-in allowlist.
        assertEquals("pics", AmbientRedditStore.normalizeSubreddit("pics"))
        assertEquals("EarthPorn", AmbientRedditStore.normalizeSubreddit("r/EarthPorn"))
        assertEquals("a_b", AmbientRedditStore.normalizeSubreddit("/r/a_b/"))
        assertEquals("TVWallpapersSFW", AmbientRedditStore.normalizeSubreddit("/r/TVWallpapersSFW/"))
        assertEquals("TVWallpapersSFW", AmbientRedditStore.normalizeSubreddit("r/TVWallpapersSFW"))
        assertEquals(
            "TVWallpapersSFW",
            AmbientRedditStore.normalizeSubreddit(
                "https://www.reddit.com/r/TVWallpapersSFW/",
            ),
        )
        assertNull(AmbientRedditStore.normalizeSubreddit("https://evil.example/r/TVWallpapersSFW/"))
        assertNull(AmbientRedditStore.normalizeSubreddit("not a subreddit"))
        assertNull(AmbientRedditStore.normalizeSubreddit("r/"))
        assertNull(AmbientRedditStore.normalizeSubreddit("../../r/pics"))
        assertNull(AmbientRedditStore.normalizeSubreddit("https://www.reddit.com/r/pics/../../x"))
    }

    @Test
    fun refresh_intervals_are_six_twelve_or_twenty_four_hours() {
        assertEquals(6 * 60 * 60 * 1000L, RedditRefresh.SIX.intervalMs())
        assertEquals(12 * 60 * 60 * 1000L, RedditRefresh.TWELVE.intervalMs())
        assertEquals(24 * 60 * 60 * 1000L, RedditRefresh.TWENTY_FOUR.intervalMs())
        assertEquals(RedditRefresh.TWELVE, RedditRefresh.fromKey(null))
        assertEquals(RedditRefresh.TWELVE, RedditRefresh.fromKey("weekly"))
    }
}
