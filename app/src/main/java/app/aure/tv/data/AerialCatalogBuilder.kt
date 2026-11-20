package app.aure.tv.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Turns Apple's `entries.json` plus its two localisation tables into the
 * bundled catalogue format. Keeping the output identical in shape is what lets
 * a refreshed file replace the asset without touching the player.
 *
 * Space and Earth name the same eighteen clips: tvOS 27 renamed the category,
 * while the older public resources still use Space. Both map to Earth so a
 * refresh never drops the theme.
 */
internal object AerialCatalogBuilder {
    const val SOURCE = "Apple TV aerial catalogue, refreshed from sylvan.apple.com/Aerials/resources.tar"

    private val CATEGORY_THEMES = linkedMapOf(
        "AerialCategoryLandscapes" to SaverTheme.LANDSCAPES,
        "AerialCategoryCities" to SaverTheme.CITIES,
        "AerialCategoryUnderwater" to SaverTheme.UNDERWATER,
        "AerialCategoryEarth" to SaverTheme.EARTH,
        "AerialCategorySpace" to SaverTheme.EARTH,
    )

    /**
     * The public tar still ships the 2019 flat schema: no categories, no name
     * keys, no previews. Replacing the bundled tvOS 27 catalogue with it would
     * lose themes, Russian names and previews, so only the category schema is
     * accepted from the network.
     */
    fun supports(entriesJson: String): Boolean {
        val root = runCatching { JSONObject(entriesJson) }.getOrNull() ?: return false
        return root.optJSONArray("categories") != null && root.optJSONArray("assets") != null
    }

    fun build(entriesJson: String, stringsEn: ByteArray, stringsRu: ByteArray): String? {
        val root = runCatching { JSONObject(entriesJson) }.getOrNull() ?: return null
        val categories = root.optJSONArray("categories") ?: return null
        val assets = root.optJSONArray("assets") ?: return null
        val en = BinaryPlist.parseStrings(stringsEn)
        val ru = BinaryPlist.parseStrings(stringsRu)
        if (en.isEmpty()) return null

        val clips = LinkedHashMap<String, JSONObject>()
        for (categoryIndex in 0 until categories.length()) {
            val category = categories.optJSONObject(categoryIndex) ?: continue
            val theme = CATEGORY_THEMES[category.optString("localizedNameKey")] ?: continue
            val categoryId = category.optString("id")
            if (categoryId.isEmpty()) continue

            var order = 0
            for (assetIndex in 0 until assets.length()) {
                val asset = assets.optJSONObject(assetIndex) ?: continue
                if (!asset.optBoolean("showInTopLevel")) continue
                if (!asset.optJSONArray("categories").containsCategory(categoryId)) continue
                val id = asset.optString("id")
                if (id.isEmpty() || clips.containsKey(id)) continue

                val key = asset.optString("localizedNameKey")
                val label = asset.optString("accessibilityLabel")
                val nameEn = en[key]?.takeIf { it.isNotBlank() }
                    ?: label.takeIf { it.isNotBlank() }
                    ?: continue
                val nameRu = ru[key]?.takeIf { it.isNotBlank() } ?: nameEn
                val video = asset.optString("url-1080-H264")
                if (!video.startsWith("http://") && !video.startsWith("https://")) continue
                val preview = listOf("previewImage-900x580", "previewImage")
                    .map { asset.optString(it) }
                    .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
                    .orEmpty()

                order++
                clips[id] = JSONObject().apply {
                    put("id", id)
                    put("nameEn", nameEn)
                    put("nameRu", nameRu)
                    put("theme", theme.key)
                    put("video", aerialStreamUrl(video))
                    put("preview", aerialStreamUrl(preview))
                    put("order", order)
                }
            }
        }
        if (clips.isEmpty()) return null

        return JSONObject().apply {
            put("version", 1)
            put("source", SOURCE)
            put("clips", JSONArray(clips.values.toList()))
        }.toString()
    }

    private fun JSONArray?.containsCategory(id: String): Boolean {
        if (this == null) return false
        for (index in 0 until length()) {
            if (optString(index) == id) return true
        }
        return false
    }
}
