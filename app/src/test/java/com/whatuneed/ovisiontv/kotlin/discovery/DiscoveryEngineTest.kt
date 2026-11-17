package com.whatuneed.ovisiontv.kotlin.discovery

import com.whatuneed.ovisiontv.kotlin.data.TvApp
import com.whatuneed.ovisiontv.kotlin.data.WatchNextRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The builder is pure: every rule below is a statement about what the profile
 * is allowed to see, not about how a provider happens to be queried.
 */
class DiscoveryEngineTest {

    private val now = 1_700_000_000_000L
    private val day = 86_400_000L

    private fun app(packageName: String, label: String = packageName) = TvApp(
        packageName = packageName,
        activityName = "$packageName.MainActivity",
        label = label,
        banner = null,
        icon = null,
    )

    private fun row(
        packageName: String,
        title: String = "Row $packageName",
        intent: String? = "intent://$title#Intent;package=$packageName;end",
        progress: Float? = null,
        continueRow: Boolean = false,
        engagement: Long = now,
        posterArtAspect: Int? = null,
        previewVideoUri: String? = null,
    ) = WatchNextRow(
        packageName = packageName,
        title = title,
        subtitle = null,
        thumbnailUri = null,
        posterUri = null,
        intentUri = intent,
        progress = progress,
        engagement = engagement,
        continueRow = continueRow,
        posterArtAspect = posterArtAspect,
        previewVideoUri = previewVideoUri,
    )

    private fun item(
        id: String,
        packageName: String,
        progress: Float? = null,
    ) = DiscoveryItem(
        id = id,
        source = DiscoverySource.TV_PROVIDER,
        kind = DiscoveryKind.VIDEO,
        packageName = packageName,
        title = id,
        intentUri = "intent://$id#Intent;package=$packageName;end",
        progress = progress,
    )

    private fun inputs(
        rows: List<WatchNextRow> = emptyList(),
        apps: List<TvApp> = listOf(app("a"), app("b"), app("c")),
        visible: Set<String> = apps.mapTo(HashSet()) { it.packageName },
        currentPackage: String? = null,
        favorites: List<DiscoveryItem> = emptyList(),
        hidden: Set<String> = emptySet(),
        usage: Map<String, Usage> = emptyMap(),
        shownAt: Map<String, Long> = emptyMap(),
        includeContinue: Boolean = true,
        personalization: Boolean = true,
        kidsMode: Boolean = false,
        ownPackage: String = "me",
        providerState: DiscoverySourceState = DiscoverySourceState.OK,
        onlineItems: List<DiscoveryItem> = emptyList(),
        onlineArtItems: List<DiscoveryItem> = emptyList(),
    ) = DiscoveryInputs(
        profileId = "p1",
        nowUtc = now,
        ownPackage = ownPackage,
        currentPackage = currentPackage,
        apps = apps,
        visiblePackages = visible,
        kidsMode = kidsMode,
        providerRows = rows,
        favorites = favorites,
        hidden = hidden,
        usage = usage,
        shownAt = shownAt,
        includeContinue = includeContinue,
        personalization = personalization,
        providerState = providerState,
        onlineItems = onlineItems,
        onlineArtItems = onlineArtItems,
    )

    private fun DiscoverySnapshot.rail(section: DiscoverySection) =
        rails.firstOrNull { it.section == section }

    private fun online(id: String, preview: Boolean = true) = DiscoveryItem(
        id = id,
        source = DiscoverySource.ONLINE,
        kind = DiscoveryKind.VIDEO,
        packageName = "me",
        packageLabel = "JustWatch",
        title = id,
        synopsis = if (preview) null else "Synopsis for $id",
        landscapeUri = "https://images.justwatch.com/backdrop/$id/s1920/film.jpg",
        posterUri = "https://images.justwatch.com/poster/$id/s592/film.jpg",
        trailerUri = if (preview) "https://video-ssl.itunes.apple.com/$id.m4v" else null,
        reason = DiscoveryReason.TRENDING,
    )

    @Test
    fun a_chart_movie_with_a_preview_becomes_a_flux_card_in_for_you() {
        val snapshot = buildSnapshot(
            inputs(
                apps = listOf(app("a"), app("app.flux.tv", "Flux")),
                onlineItems = listOf(
                    online("justwatch|1").copy(
                        title = "Дюна",
                        originalTitle = "Dune",
                        tmdbId = "438631",
                    ),
                ),
            ),
        )

        val card = snapshot.rail(DiscoverySection.FOR_YOU)?.items?.single()
        assertEquals(DiscoverySource.TV_PROVIDER, card?.source)
        assertEquals("app.flux.tv", card?.packageName)
        assertEquals("Flux", card?.packageLabel)
        assertEquals("flux://movie/438631", card?.intentUri)
        assertEquals("https://video-ssl.itunes.apple.com/justwatch|1.m4v", card?.trailerUri)
        assertNull(snapshot.rail(DiscoverySection.ONLINE))
    }

    @Test
    fun a_chart_movie_without_a_preview_is_not_moved_into_for_you() {
        val snapshot = buildSnapshot(
            inputs(
                apps = listOf(app("app.flux.tv", "Flux")),
                onlineItems = listOf(online("justwatch|1", preview = false).copy(tmdbId = "438631")),
            ),
        )
        assertNull(snapshot.rail(DiscoverySection.FOR_YOU))
    }

    @Test
    fun onlineChartIsMetadataOnlyAndNeverBecomesItsOwnTab() {
        val snapshot = buildSnapshot(
            inputs(onlineItems = listOf(online("apple|1"), online("apple|2"))),
        )

        assertTrue(snapshot.rail(DiscoverySection.ONLINE) == null)
        assertTrue(snapshot.rails.isEmpty())
        assertNull(defaultDiscoverySection(snapshot.rails))
    }

    @Test
    fun the_launcher_never_recommends_itself() {
        val snapshot = buildSnapshot(
            inputs(rows = listOf(row("me"), row("a"), row("b"), row("c"))),
        )
        assertTrue(snapshot.rails.flatMap { it.items }.none { it.packageName == "me" })
    }

    @Test
    fun a_package_the_profile_cannot_see_never_appears() {
        val snapshot = buildSnapshot(
            inputs(
                rows = listOf(row("a"), row("b"), row("c"), row("secret")),
                visible = setOf("a", "b", "c"),
            ),
        )
        assertTrue(snapshot.rails.flatMap { it.items }.none { it.packageName == "secret" })
    }

    @Test
    fun hidden_cards_are_dropped_everywhere() {
        val hidden = setOf("tv|a|intent://Row a#Intent;package=a;end")
        val snapshot = buildSnapshot(
            inputs(rows = listOf(row("a"), row("b"), row("c")), hidden = hidden),
        )
        assertTrue(snapshot.rails.flatMap { it.items }.none { it.id in hidden })
    }

    @Test
    fun a_row_without_a_title_or_intent_is_not_a_card() {
        val snapshot = buildSnapshot(
            inputs(
                rows = listOf(
                    row("a", title = "  "),
                    row("b", intent = null),
                    row("c"),
                ),
            ),
        )
        // "c" is valid and may form a one-card For You rail; the test only
        // proves that the two broken rows never became cards.
        val items = snapshot.rails.flatMap { it.items }
        assertTrue(items.none { it.packageName == "a" || it.packageName == "b" })
    }

    @Test
    fun continue_watching_keeps_one_real_resume_point() {
        val snapshot = buildSnapshot(
            inputs(
                rows = listOf(
                    row("a", progress = 0.4f),
                    row("b"),
                    row("c"),
                ),
            ),
        )
        val continueRail = snapshot.rail(DiscoverySection.CONTINUE)
        assertEquals(listOf("a"), continueRail?.items?.map { it.packageName })
        // For You never repeats what Continue already shows, but it still
        // carries the other two app recommendations.
        assertEquals(
            listOf("b", "c"),
            snapshot.rail(DiscoverySection.FOR_YOU)?.items?.map { it.packageName },
        )
    }

    @Test
    fun turning_continue_off_removes_resume_rows_from_every_rail() {
        val snapshot = buildSnapshot(
            inputs(
                rows = listOf(row("a", progress = 0.4f), row("b"), row("c")),
                includeContinue = false,
            ),
        )
        assertTrue(snapshot.rail(DiscoverySection.CONTINUE) == null)
        assertTrue(snapshot.rails.flatMap { it.items }.none { it.progress != null })
    }

    @Test
    fun a_single_app_recommendation_is_still_a_rail() {
        val one = buildSnapshot(inputs(rows = listOf(row("a"))))
        assertEquals(1, one.rail(DiscoverySection.FOR_YOU)?.items?.size)

        val three = buildSnapshot(inputs(rows = listOf(row("a"), row("b"), row("c"))))
        assertEquals(3, three.rail(DiscoverySection.FOR_YOU)?.items?.size)
    }

    @Test
    fun from_this_app_contains_only_the_focused_package() {
        val snapshot = buildSnapshot(
            inputs(
                rows = listOf(row("a"), row("b"), row("c")),
                currentPackage = "b",
            ),
        )
        val fromApp = snapshot.rail(DiscoverySection.FROM_APP)
        assertEquals(listOf("b"), fromApp?.items?.map { it.packageName })
    }

    @Test
    fun an_old_row_stays_on_its_own_app_shelf_but_not_in_for_you() {
        val old = now - 60 * day
        val snapshot = buildSnapshot(
            inputs(
                rows = listOf(
                    row("a", engagement = old),
                    row("b", engagement = old),
                    row("c", engagement = old),
                ),
                currentPackage = "a",
            ),
        )
        assertEquals(listOf("a"), snapshot.rail(DiscoverySection.FROM_APP)?.items?.map { it.packageName })
        assertTrue(snapshot.rail(DiscoverySection.FOR_YOU) == null)
    }

    @Test
    fun recently_used_only_counts_quiet_launcher_history() {
        val usage = mapOf(
            "a" to Usage(4, now - day),
            "b" to Usage(2, now - 2 * day),
            "c" to Usage(1, now - 40 * day),
        )
        val snapshot = buildSnapshot(inputs(usage = usage))
        val recent = snapshot.rail(DiscoverySection.RECENT)
        assertEquals(listOf("a", "b"), recent?.items?.map { it.packageName })
        assertEquals(DiscoveryReason.RECENTLY_LAUNCHED, recent?.items?.first()?.reason)
    }

    @Test
    fun personalization_off_removes_recent_but_keeps_explicit_favorites() {
        val usage = mapOf("a" to Usage(4, now), "b" to Usage(2, now))
        val favorites = listOf(item("fav", "c"))
        val snapshot = buildSnapshot(
            inputs(usage = usage, favorites = favorites, personalization = false),
        )
        assertTrue(snapshot.rail(DiscoverySection.RECENT) == null)
        assertEquals(listOf("c"), snapshot.rail(DiscoverySection.FAVORITES)?.items?.map { it.packageName })
    }

    @Test
    fun a_favorite_for_an_uninstalled_app_or_a_kids_hidden_app_is_not_shown() {
        val snapshot = buildSnapshot(
            inputs(
                apps = listOf(app("a")),
                visible = setOf("a"),
                favorites = listOf(item("gone", "gone"), item("blocked", "blocked")),
                kidsMode = true,
            ),
        )
        assertTrue(snapshot.rail(DiscoverySection.FAVORITES) == null)
    }

    @Test
    fun five_populated_rails_are_capped_at_four_by_dropping_recent() {
        val apps = listOf(app("a"), app("b"), app("c"), app("d"))
        val snapshot = buildSnapshot(
            inputs(
                apps = apps,
                visible = apps.mapTo(HashSet()) { it.packageName },
                rows = listOf(
                    row("a", progress = 0.4f),
                    row("b"),
                    row("c"),
                    row("d"),
                ),
                currentPackage = "d",
                usage = mapOf("a" to Usage(3, now), "b" to Usage(2, now)),
                favorites = listOf(item("fav", "c")),
            ),
        )
        assertEquals(4, snapshot.rails.size)
        assertTrue(snapshot.rails.none { it.section == DiscoverySection.RECENT })
        assertEquals(
            setOf(
                DiscoverySection.CONTINUE,
                DiscoverySection.FOR_YOU,
                DiscoverySection.FROM_APP,
                DiscoverySection.FAVORITES,
            ),
            snapshot.rails.map { it.section }.toSet(),
        )
    }

    @Test
    fun a_kids_profile_keeps_only_the_app_specific_rails() {
        val snapshot = buildSnapshot(
            inputs(
                rows = listOf(row("a"), row("b"), row("c")),
                currentPackage = "b",
                favorites = listOf(item("fav", "a")),
                usage = mapOf("a" to Usage(3, now), "b" to Usage(2, now)),
                kidsMode = true,
            ),
        )
        assertEquals(
            listOf(DiscoverySection.FROM_APP),
            snapshot.rails.map { it.section },
        )
        assertTrue(snapshot.rails.single().items.all { it.packageName == "b" })
    }

    @Test
    fun a_favorite_of_a_visible_installed_app_is_kept() {
        val snapshot = buildSnapshot(
            inputs(favorites = listOf(item("fav", "b"))),
        )
        assertEquals(listOf("b"), snapshot.rail(DiscoverySection.FAVORITES)?.items?.map { it.packageName })
    }

    @Test
    fun shown_ids_are_keyed_by_section_but_ranked_per_item() {
        val shown = mapOf(
            "CONTINUE|one" to now - 1_000L,
            "FOR_YOU|one" to now,
            "FOR_YOU|two" to now - 5_000L,
        )
        val byItem = shownIdsByItem(shown)
        assertEquals(now, byItem["one"])
        assertEquals(now - 5_000L, byItem["two"])
    }

    @Test
    fun a_provider_failure_still_leaves_the_launcher_owned_recent_rail() {
        val snapshot = buildSnapshot(
            inputs(
                rows = emptyList(),
                usage = mapOf("a" to Usage(3, now), "b" to Usage(2, now)),
                providerState = DiscoverySourceState.FAILED,
            ),
        )
        assertEquals(listOf("a", "b"), snapshot.rail(DiscoverySection.RECENT)?.items?.map { it.packageName })
        assertEquals(DiscoverySourceState.FAILED, snapshot.providerState)
    }

    @Test
    fun installed_app_recommendations_lead_before_the_online_chart() {
        val snapshot = buildSnapshot(
            inputs(
                rows = listOf(row("a"), row("b"), row("c")),
                onlineItems = listOf(online("justwatch|1")),
            ),
        )

        assertEquals(
            listOf(DiscoverySection.FOR_YOU),
            snapshot.rails.map { it.section },
        )
    }

    @Test
    fun a_wide_provider_poster_is_used_as_the_card_art() {
        val wide = "https://provider.example/wide.jpg"
        val snapshot = buildSnapshot(
            inputs(
                rows = listOf(
                    row("a", title = "Dune").copy(posterUri = wide, posterArtAspect = 0),
                ),
            ),
        )

        val card = snapshot.rail(DiscoverySection.FOR_YOU)?.items?.single()
        assertEquals(wide, card?.landscapeUri)
        assertEquals(wide, card?.posterUri)
    }

    @Test
    fun a_portrait_provider_poster_is_not_stretched_into_the_wide_slot() {
        val portrait = "https://provider.example/poster.jpg"
        val snapshot = buildSnapshot(
            inputs(
                rows = listOf(
                    row("a", title = "Dune").copy(posterUri = portrait, posterArtAspect = 4),
                ),
            ),
        )

        val card = snapshot.rail(DiscoverySection.FOR_YOU)?.items?.single()
        assertEquals(null, card?.landscapeUri)
        assertEquals(portrait, card?.posterUri)
    }

    @Test
    fun a_provider_row_without_art_gets_exact_justwatch_art() {
        val snapshot = buildSnapshot(
            inputs(
                rows = listOf(row("a", title = "Dune")),
                onlineItems = listOf(
                    online("justwatch|1").copy(
                        title = "Dune",
                        landscapeUri = "https://images.justwatch.com/backdrop/dune/s1920.jpg",
                        posterUri = "https://images.justwatch.com/poster/dune/s592.jpg",
                        trailerUri = "https://video-ssl.itunes.apple.com/dune.m4v",
                    ),
                ),
            ),
        )

        val card = snapshot.rail(DiscoverySection.FOR_YOU)?.items?.single()
        assertEquals("https://images.justwatch.com/backdrop/dune/s1920.jpg", card?.landscapeUri)
        assertEquals("https://images.justwatch.com/poster/dune/s592.jpg", card?.posterUri)
    }

    @Test
    fun cached_online_art_can_decorate_a_provider_row_without_an_online_rail() {
        val snapshot = buildSnapshot(
            inputs(
                rows = listOf(row("a", title = "Dune")),
                onlineArtItems = listOf(
                    online("justwatch|1").copy(
                        title = "Dune",
                        landscapeUri = "https://images.justwatch.com/backdrop/dune/s1920.jpg",
                        posterUri = "https://images.justwatch.com/poster/dune/s592.jpg",
                        trailerUri = "https://video-ssl.itunes.apple.com/dune.m4v",
                    ),
                ),
            ),
        )

        assertEquals(null, snapshot.rail(DiscoverySection.ONLINE))
        val card = snapshot.rail(DiscoverySection.FOR_YOU)?.items?.single()
        assertEquals("https://images.justwatch.com/backdrop/dune/s1920.jpg", card?.landscapeUri)
        assertEquals("https://images.justwatch.com/poster/dune/s592.jpg", card?.posterUri)
        assertEquals("https://video-ssl.itunes.apple.com/dune.m4v", card?.trailerUri)
    }

    @Test
    fun a_provider_row_keeps_its_own_art_over_justwatch() {
        val own = "https://provider.example/own.jpg"
        val snapshot = buildSnapshot(
            inputs(
                rows = listOf(
                    row("a", title = "Dune").copy(
                        thumbnailUri = own,
                        posterUri = own,
                    ),
                ),
                onlineItems = listOf(
                    online("justwatch|1").copy(title = "Dune"),
                ),
            ),
        )

        val card = snapshot.rail(DiscoverySection.FOR_YOU)?.items?.single()
        assertEquals(own, card?.landscapeUri)
        assertEquals(own, card?.posterUri)
    }

    @Test
    fun a_flux_movie_deep_link_matches_justwatch_by_tmdb_even_when_titles_differ() {
        val snapshot = buildSnapshot(
            inputs(
                apps = listOf(app("app.flux.tv", "Flux")),
                rows = listOf(
                    row(
                        "app.flux.tv",
                        title = "Dune",
                        intent = "flux://movie/438631",
                    ),
                ),
                onlineItems = listOf(
                    online("justwatch|1").copy(
                        title = "Дюна",
                        originalTitle = "Dune",
                        tmdbId = "438631",
                        trailerUri = "https://video-ssl.itunes.apple.com/dune.m4v",
                    ),
                ),
            ),
        )

        val card = snapshot.rail(DiscoverySection.FOR_YOU)?.items?.single()
        assertEquals("Dune", card?.title)
        assertEquals("https://video-ssl.itunes.apple.com/dune.m4v", card?.trailerUri)
    }

    @Test
    fun a_provider_row_can_borrow_art_and_trailer_by_the_original_title() {
        val snapshot = buildSnapshot(
            inputs(
                rows = listOf(row("a", title = "Dune")),
                onlineItems = listOf(
                    online("justwatch|1").copy(
                        title = "Дюна",
                        originalTitle = "Dune",
                        trailerUri = "https://video-ssl.itunes.apple.com/dune.m4v",
                    ),
                ),
            ),
        )

        val card = snapshot.rail(DiscoverySection.FOR_YOU)?.items?.single()
        assertEquals("Dune", card?.title)
        assertEquals("https://video-ssl.itunes.apple.com/dune.m4v", card?.trailerUri)
    }

    @Test
    fun a_provider_published_preview_becomes_the_card_trailer() {
        val snapshot = buildSnapshot(
            inputs(
                rows = listOf(
                    row(
                        "a",
                        title = "Provider clip",
                        previewVideoUri = "https://cdn.provider.example/clip.mp4",
                    ),
                ),
            ),
        )

        val card = snapshot.rail(DiscoverySection.FOR_YOU)?.items?.single()
        assertEquals("https://cdn.provider.example/clip.mp4", card?.trailerUri)
    }

    @Test
    fun a_provider_preview_that_is_a_web_page_is_not_played_as_media() {
        assertNull(playableProviderPreview(null))
        assertNull(playableProviderPreview("http://cdn.provider.example/clip.mp4"))
        assertNull(playableProviderPreview("https://www.youtube.com/watch?v=abc"))
        assertNull(playableProviderPreview("https://youtu.be/abc"))
        assertEquals(
            "https://cdn.provider.example/clip.mp4",
            playableProviderPreview("https://cdn.provider.example/clip.mp4"),
        )
    }

    @Test
    fun a_provider_row_without_a_matching_online_title_has_no_trailer_step() {
        val snapshot = buildSnapshot(
            inputs(
                rows = listOf(row("a", title = "Dune")),
                onlineItems = listOf(
                    online("justwatch|1").copy(
                        title = "A different film",
                        trailerUri = "https://video-ssl.itunes.apple.com/other.m4v",
                    ),
                ),
            ),
        )

        val card = snapshot.rail(DiscoverySection.FOR_YOU)?.items?.single()
        assertEquals(null, card?.trailerUri)
    }

    @Test
    fun a_provider_row_gets_the_matching_synopsis_for_its_own_overlay() {
        val snapshot = buildSnapshot(
            inputs(
                rows = listOf(row("a", title = "Dune")),
                onlineItems = listOf(
                    online("justwatch|1").copy(
                        title = "Dune",
                        synopsis = "A real synopsis.",
                    ),
                ),
            ),
        )

        assertEquals("A real synopsis.", snapshot.rail(DiscoverySection.FOR_YOU)?.items?.single()?.synopsis)
    }

    @Test
    fun an_online_card_can_find_the_provider_row_that_should_open_it() {
        val snapshot = buildSnapshot(
            inputs(
                rows = listOf(row("a", title = "Dune", intent = "flux://movie/438631")),
                onlineItems = listOf(
                    online("justwatch|1").copy(
                        title = "Дюна",
                        originalTitle = "Dune",
                        tmdbId = "438631",
                    ),
                ),
            ),
        )

        val online = online("justwatch|1").copy(
            title = "Дюна",
            originalTitle = "Dune",
            tmdbId = "438631",
            trailerUri = "https://video-ssl.itunes.apple.com/dune.m4v",
        )
        val target = providerTargetForOnline(online, snapshot.rails)
        assertEquals("a", target?.packageName)
        assertEquals("flux://movie/438631", target?.intentUri)
    }

    @Test
    fun the_snapshot_carries_the_provider_health_for_settings() {
        val snapshot = buildSnapshot(
            inputs(providerState = DiscoverySourceState.NO_PERMISSION),
        )
        assertEquals(DiscoverySourceState.NO_PERMISSION, snapshot.providerState)
        assertTrue(snapshot.rails.isEmpty())
    }
}
