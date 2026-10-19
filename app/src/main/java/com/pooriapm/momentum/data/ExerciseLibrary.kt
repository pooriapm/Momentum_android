package com.pooriapm.momentum.data

import android.content.Context
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import com.pooriapm.momentum.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.File
import java.util.zip.ZipInputStream

@Serializable
data class LibraryExercise(
    val id: String,
    val fa: String,
    val en: String,
    val cat: String,
    val muscles: String,
    val steps: List<String>,
    val anim: String? = null,
    val eq: List<String> = emptyList(),
    val aka: List<String> = emptyList(),
    val tip: String? = null,
)

@Serializable
data class ExerciseLibraryData(
    val version: Int,
    val categories: Map<String, String> = emptyMap(),
    val exercises: List<LibraryExercise> = emptyList(),
)

/**
 * The exercise library (steps, tips, animations) lives on the server, not in the APK. The list is a
 * small zip fetched once and refreshed every few days; when a plan arrives, the animations of its
 * exercises are downloaded once and kept on the phone. Opening a guide then reads only the phone,
 * so it is instant and works offline.
 */
object ExerciseLibrary {
    /** Public storage; momentum.properties can point a debug build at a local server instead. */
    val baseUrl: String = BuildConfig.EXERCISE_MEDIA_URL.ifBlank {
        "${BuildConfig.SUPABASE_URL.trimEnd('/')}/storage/v1/object/public/exercise-media/v1/"
    }.let { if (it.endsWith('/')) it else "$it/" }

    private const val REFRESH_MS = 3L * 24 * 60 * 60 * 1000
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()

    @Volatile private var loaded: Index? = null

    /** What this process has already read, so a screen can draw before [load] returns. */
    val cached: Index? get() = loaded

    private fun animationUrl(anim: String) = "${baseUrl}anim/$anim.zip"

    private fun animationFile(context: Context, anim: String) =
        File(File(context.filesDir, "exercise-anim").apply { mkdirs() }, "$anim.zip")

    /** Downloads every animation the plan's workouts use that the phone does not have yet. */
    suspend fun prepare(context: Context, exercises: List<Pair<String?, String>>) {
        val index = load(context) ?: return
        val needed = exercises.mapNotNullTo(HashSet()) { (key, name) -> index.find(key, name)?.anim }
        withContext(Dispatchers.IO) {
            for (anim in needed) {
                val file = animationFile(context, anim)
                if (!file.exists()) runCatching { download(anim, file) }
            }
        }
    }

    /** One animation: from the phone, or downloaded (and kept) if the plan changed since. */
    suspend fun animation(context: Context, anim: String): LottieComposition? = withContext(Dispatchers.IO) {
        val file = animationFile(context, anim)
        if (!file.exists()) runCatching { download(anim, file) }
        if (!file.exists()) return@withContext null
        // Lottie keeps parsed animations in memory by this key, so a list and its guide share one.
        runCatching {
            ZipInputStream(file.inputStream()).use { LottieCompositionFactory.fromZipStreamSync(context, it, "exercise:$anim").value }
        }.getOrNull() ?: run { file.delete(); null }
    }

    private fun download(anim: String, target: File) {
        val request = Request.Builder().url(animationUrl(anim)).build()
        sharedHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return
            val bytes = response.body?.bytes() ?: return
            // Written whole, then renamed: a cut connection never leaves half a file behind.
            val part = File(target.parentFile, target.name + ".part")
            part.writeBytes(bytes)
            part.renameTo(target)
        }
    }

    /** The cached library at once when there is one; downloads it the first time and when stale. */
    suspend fun load(context: Context): Index? = lock.withLock {
        withContext(Dispatchers.IO) {
            val file = File(context.filesDir, "exercise-library.json")
            val cached = loaded ?: file.takeIf { it.exists() }?.let { parse(it.readText()) }
            val stale = !file.exists() || System.currentTimeMillis() - file.lastModified() > REFRESH_MS
            val fresh = if (stale) runCatching { download() }.getOrNull() else null
            if (fresh != null) {
                parse(fresh)?.let {
                    file.writeText(fresh)
                    loaded = it
                    return@withContext it
                }
            }
            loaded = cached
            cached
        }
    }

    private fun download(): String? {
        val request = Request.Builder().url("${baseUrl}library.zip").build()
        sharedHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            ZipInputStream(response.body?.byteStream() ?: return null).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: return null
                    if (entry.name.endsWith(".json")) return zip.readBytes().toString(Charsets.UTF_8)
                }
            }
        }
    }

    private fun parse(text: String): Index? =
        runCatching { Index(json.decodeFromString(ExerciseLibraryData.serializer(), text)) }.getOrNull()

    /** Lookup by catalog id or by name: plans from the AI name exercises in their own words. */
    class Index(val data: ExerciseLibraryData) {
        private val byKey = HashMap<String, LibraryExercise>()
        private val names = ArrayList<Pair<List<String>, LibraryExercise>>()

        init {
            for (e in data.exercises) {
                byKey[e.id] = e
                for (name in listOf(e.fa, e.en) + e.aka) {
                    val key = normalize(name)
                    if (key.length >= 3) byKey.putIfAbsent(key, e)
                    words(name).takeIf { it.isNotEmpty() }?.let { names += it to e }
                }
            }
        }

        fun find(exerciseKey: String?, name: String): LibraryExercise? {
            exerciseKey?.let { key ->
                // Catalog ids look like "exercise:goblet-squat@v2"; the library uses the slug.
                val slug = CATALOG.matchEntire(key)?.groupValues?.get(1) ?: key
                byKey[slug]?.let { return it }
            }
            val wanted = normalize(name)
            if (wanted.isEmpty()) return null
            byKey[wanted]?.let { return it }
            // Word by word, in any order: "کتل بل گابلت اسکوات" finds "اسکات گابلت", and
            // "زیر بغل سیم کش" finds "زیربغل سیم‌کش نشسته". Most of a name's words must appear;
            // the name covering the most letters wins, a complete one a little more.
            var best: LibraryExercise? = null
            var bestScore = 0
            for ((parts, e) in names) {
                var matched = 0
                var letters = 0
                for (part in parts) if (wanted.contains(part)) { matched++; letters += part.length }
                if (letters < 4 || matched * 10 < parts.size * 6) continue
                val score = letters * 2 + if (matched == parts.size) 3 else 0
                if (score > bestScore) { bestScore = score; best = e }
            }
            return best
        }

        fun category(key: String) = data.categories[key]
    }

    private val CATALOG = Regex("""^exercise:([a-z0-9._-]+)@v\d+$""")

    /** Words that say nothing about which exercise it is. */
    private val FILLER = setOf("با", "و", "روی", "از", "در", "به", "حرکت", "the", "with", "on", "and")

    private fun words(name: String): List<String> =
        name.split(' ', '‌', '-', '/', '،', ',').filter { it.isNotBlank() && it.lowercase() !in FILLER }
            .map(::normalize).filter { it.length >= 2 }

    /** Lower case, Arabic letters as Persian, digits as ASCII, and no spaces, half-spaces or dashes. */
    fun normalize(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text.lowercase()) {
            when (ch) {
                'ي', 'ى' -> sb.append('ی')
                'ك' -> sb.append('ک')
                'ة' -> sb.append('ه')
                'أ', 'إ', 'آ' -> sb.append('ا')
                in '۰'..'۹' -> sb.append('0' + (ch - '۰'))
                in '٠'..'٩' -> sb.append('0' + (ch - '٠'))
                ' ', '‌', '‏', '-', '_', '.', '(', ')', '،', ',', '/' -> Unit
                else -> if (ch !in 'ً'..'ْ') sb.append(ch)
            }
        }
        // Common spellings of the same word.
        return sb.toString().replace("اسکوات", "اسکات").replace("ۀ", "ه")
    }
}
