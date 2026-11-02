package fr.floran.vraisbonsplans.scan

import android.content.Context
import fr.floran.vraisbonsplans.model.Deal
import org.json.JSONArray
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Result of the last scan, as shown on the main screen. */
data class ScanResult(
    val finishedAtMillis: Long,
    val deals: List<Deal>,
    /** Big discounts that no second source could confirm: shown, never notified. */
    val dealsToVerify: List<Deal>,
    val fakeDeals: List<Deal>,
    /** One readable line per source that failed, e.g. "idealo : HTTP 403". */
    val errors: List<String>,
)

/** Last scan result plus the notified / ignored product keys, persisted in SharedPreferences. */
object ScanStore {

    private const val PREFERENCES_NAME = "scan"
    private const val KEY_FINISHED_AT = "finished_at"
    private const val KEY_DEALS = "deals"
    private const val KEY_DEALS_TO_VERIFY = "deals_to_verify"
    private const val KEY_FAKE_DEALS = "fake_deals"
    private const val KEY_ERRORS = "errors"
    private const val KEY_NOTIFIED = "notified_keys"
    private const val KEY_IGNORED = "ignored_keys"
    private const val MAXIMUM_REMEMBERED_KEYS = 500

    private val lastResultFlow = MutableStateFlow<ScanResult?>(null)

    /** Emits the stored result, then every new one saved while the app process is alive. */
    fun lastResult(context: Context): StateFlow<ScanResult?> {
        if (lastResultFlow.value == null) lastResultFlow.value = load(context)
        return lastResultFlow.asStateFlow()
    }

    fun save(context: Context, result: ScanResult) {
        preferences(context).edit()
            .putLong(KEY_FINISHED_AT, result.finishedAtMillis)
            .putString(KEY_DEALS, Deal.listToJson(result.deals))
            .putString(KEY_DEALS_TO_VERIFY, Deal.listToJson(result.dealsToVerify))
            .putString(KEY_FAKE_DEALS, Deal.listToJson(result.fakeDeals))
            .putString(KEY_ERRORS, JSONArray(result.errors).toString())
            .apply()
        lastResultFlow.value = result
    }

    fun notifiedKeys(context: Context): Set<String> = readKeys(context, KEY_NOTIFIED)

    fun addNotifiedKeys(context: Context, keys: Collection<String>) = appendKeys(context, KEY_NOTIFIED, keys)

    fun ignoredKeys(context: Context): Set<String> = readKeys(context, KEY_IGNORED)

    fun ignore(context: Context, key: String) {
        appendKeys(context, KEY_IGNORED, listOf(key))
        lastResultFlow.value = lastResultFlow.value?.let { result ->
            result.copy(
                deals = result.deals.filterNot { it.key == key },
                dealsToVerify = result.dealsToVerify.filterNot { it.key == key },
            )
        }
    }

    private fun load(context: Context): ScanResult? {
        val preferences = preferences(context)
        val finishedAt = preferences.getLong(KEY_FINISHED_AT, 0L)
        if (finishedAt == 0L) return null
        val errors = JSONArray(preferences.getString(KEY_ERRORS, "[]"))
        val ignored = ignoredKeys(context)
        return ScanResult(
            finishedAtMillis = finishedAt,
            deals = Deal.listFromJson(preferences.getString(KEY_DEALS, null)).filterNot { it.key in ignored },
            dealsToVerify = Deal.listFromJson(preferences.getString(KEY_DEALS_TO_VERIFY, null)).filterNot { it.key in ignored },
            fakeDeals = Deal.listFromJson(preferences.getString(KEY_FAKE_DEALS, null)),
            errors = (0 until errors.length()).map { errors.getString(it) },
        )
    }

    private fun readKeys(context: Context, preferenceKey: String): Set<String> {
        val array = JSONArray(preferences(context).getString(preferenceKey, "[]"))
        return (0 until array.length()).map { array.getString(it) }.toSet()
    }

    /** Keeps insertion order so the oldest keys are dropped first. */
    private fun appendKeys(context: Context, preferenceKey: String, keys: Collection<String>) {
        val array = JSONArray(preferences(context).getString(preferenceKey, "[]"))
        val merged = LinkedHashSet<String>().apply {
            (0 until array.length()).forEach { add(array.getString(it)) }
            addAll(keys)
        }.toList().takeLast(MAXIMUM_REMEMBERED_KEYS)
        preferences(context).edit().putString(preferenceKey, JSONArray(merged).toString()).apply()
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}
