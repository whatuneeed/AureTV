package com.whatuneed.ovisiontv.kotlin.backup

import android.content.Context
import com.whatuneed.ovisiontv.kotlin.ambient.AmbientSettingsStore
import com.whatuneed.ovisiontv.kotlin.data.AerialQuality
import com.whatuneed.ovisiontv.kotlin.data.AppLabelStore
import com.whatuneed.ovisiontv.kotlin.data.ContentPreferences
import com.whatuneed.ovisiontv.kotlin.data.HomeOrderStore
import com.whatuneed.ovisiontv.kotlin.data.KidsStore
import com.whatuneed.ovisiontv.kotlin.data.LocalePreference
import com.whatuneed.ovisiontv.kotlin.data.ProfileStore
import com.whatuneed.ovisiontv.kotlin.data.SaverPreferences
import com.whatuneed.ovisiontv.kotlin.data.StartupPreferences
import com.whatuneed.ovisiontv.kotlin.data.WeatherPreferences
import com.whatuneed.ovisiontv.kotlin.data.showPhoneApps
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoverySettings
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryStore

internal data class SourceRead(
    val data: BackupData,
    val warnings: List<BackupWarning>,
)

/** Reads the existing stores; it never caches or writes a second copy of them. */
internal class BackupSource(private val context: Context) {

    fun read(selection: BackupSelection): SourceRead {
        val warnings = LinkedHashSet<BackupWarning>()
        val profileStore = ProfileStore(context)
        val profiles = profileStore.list()
        val current = profileStore.current().id
        val profileData = ProfilesData(
            profiles = profiles.map {
                BackupProfile(it.id, it.name, it.emoji, it.color, it.kids)
            },
            currentId = current,
            nextId = profileStore.nextIdFloor(),
            askOnBoot = profileStore.askOnBoot(),
            askOnWake = profileStore.askOnWake(),
        )

        val homes = LinkedHashMap<String, HomeData>()
        val discovery = LinkedHashMap<String, DiscoveryData>()
        val kids = LinkedHashMap<String, KidsData>()
        profiles.forEach { profile ->
            val prefsName = ProfileStore.homePrefsName(profile.id)
            if (selection.home) {
                val raw = HomeOrderStore(context, prefsName).savedOrder()
                try {
                    homes[profile.id] = BackupJson.decodeHome(raw).copy(
                        labels = AppLabelStore.labels(context, prefsName),
                    )
                } catch (_: Exception) {
                    warnings += BackupWarning.SOURCE_UNREADABLE
                }
            }
            if (selection.discovery) {
                val store = DiscoveryStore(context, prefsName)
                val settings = DiscoverySettings(context, prefsName)
                discovery[profile.id] = DiscoveryData(
                    recommendations = settings.recommendations(),
                    continueWatching = settings.continueWatching(),
                    personalization = settings.personalization(),
                    favorites = store.favorites(),
                    hidden = store.hidden(),
                    usage = store.usage(),
                    shown = store.shownAt(),
                )
            }
            if (selection.kids) {
                kids[profile.id] = KidsData(KidsStore(context).allowlist(profile.id))
            }
        }

        val weather = if (selection.weather) {
            WeatherData(
                enabled = WeatherPreferences.enabled(context),
                manual = WeatherPreferences.manual(context),
                place = WeatherPreferences.place(context),
                latitude = WeatherPreferences.latitude(context),
                longitude = WeatherPreferences.longitude(context),
                units = WeatherPreferences.units(context),
                source = WeatherPreferences.source(context),
                refreshMinutes = WeatherPreferences.refreshMinutes(context),
            )
        } else {
            null
        }

        val saver = if (selection.saver) {
            SaverData(
                delayMinutes = SaverPreferences.delay(context).minutes,
                music = SaverPreferences.music(context),
                refresh = SaverPreferences.refresh(context).name,
                themes = SaverPreferences.themes(context).mapTo(LinkedHashSet()) { it.key },
                hidden = SaverPreferences.hidden(context),
                mode = AmbientSettingsStore.mode(context).key,
                clockFormat = AmbientSettingsStore.clockFormat(context).key,
                pixelShift = AmbientSettingsStore.pixelShift(context),
                dimAfter = AmbientSettingsStore.dimAfter(context).key,
                maxSession = AmbientSettingsStore.maxSession(context).key,
                showWeather = AmbientSettingsStore.showWeather(context),
                aerialQuality = AmbientSettingsStore.aerialQuality(context).key,
            )
        } else {
            null
        }

        return SourceRead(
            data = BackupData(
                profiles = if (selection.profiles) profileData else null,
                homes = homes,
                discovery = discovery,
                kids = kids,
                weather = weather,
                saver = saver,
                language = if (selection.language) {
                    LanguageData(
                        language = LocalePreference.get(context),
                        showPhoneApps = showPhoneApps(context),
                    )
                } else {
                    null
                },
                content = if (selection.content) {
                    ContentData(
                        recommendations = ContentPreferences.recommendations(context),
                        continueWatching = ContentPreferences.continueWatching(context),
                    )
                } else {
                    null
                },
                startup = if (selection.startup) {
                    StartupData(
                        target = StartupPreferences.target(context),
                        label = StartupPreferences.label(context),
                        onWake = StartupPreferences.onWake(context),
                    )
                } else {
                    null
                },
            ),
            warnings = warnings.toList(),
        )
    }
}
