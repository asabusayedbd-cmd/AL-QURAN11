package com.example.core.database

import android.content.Context
import android.util.JsonReader
import java.io.InputStreamReader

object DuaSeederData {
    const val EXPECTED_MIN_DUA_COUNT = 300
    private const val ASSET_PATH = "duas/duas_catalog.json"

    fun streamChunksFromAssets(
        context: Context,
        chunkSize: Int = 30,
        onChunk: suspend (List<DuaEntity>) -> Unit
    ): Int {
        // Parse synchronously inside a helper and return parsed items in bounded chunks via callback,
        // or return the sequence of chunks so suspend inserts run smoothly inside a Room transaction.
        throw UnsupportedOperationException("Use readInChunks(context, chunkSize) instead")
    }

    fun readInChunks(context: Context, chunkSize: Int = 30): Sequence<List<DuaEntity>> = sequence {
        val reader = JsonReader(InputStreamReader(context.assets.open(ASSET_PATH), Charsets.UTF_8))
        reader.use { jr ->
            val batch = ArrayList<DuaEntity>(chunkSize)
            jr.beginArray()
            while (jr.hasNext()) {
                var id = 0
                var category = ""
                var title = ""
                var arabicText = ""
                var transliteration = ""
                var translation = ""
                var reference = ""
                var virtueOrContext = ""
                var audioUrl = ""

                jr.beginObject()
                while (jr.hasNext()) {
                    when (jr.nextName()) {
                        "id" -> id = jr.nextInt()
                        "category" -> category = jr.nextString()
                        "title" -> title = jr.nextString()
                        "arabicText" -> arabicText = jr.nextString()
                        "transliteration" -> transliteration = jr.nextString()
                        "translation" -> translation = jr.nextString()
                        "reference" -> reference = jr.nextString()
                        "virtueOrContext" -> virtueOrContext = jr.nextString()
                        "audioUrl" -> audioUrl = jr.nextString()
                        else -> jr.skipValue()
                    }
                }
                jr.endObject()

                if (id > 0 && title.isNotBlank() && arabicText.isNotBlank()) {
                    batch.add(
                        DuaEntity(
                            id = id,
                            category = category,
                            title = title,
                            arabicText = arabicText,
                            transliteration = transliteration,
                            translation = translation,
                            reference = reference,
                            virtueOrContext = virtueOrContext,
                            audioUrl = audioUrl,
                            isBookmarked = false
                        )
                    )
                }

                if (batch.size >= chunkSize) {
                    yield(batch.toList())
                    batch.clear()
                }
            }
            jr.endArray()
            if (batch.isNotEmpty()) {
                yield(batch.toList())
            }
        }
    }

    fun loadAllFromAssets(context: Context): List<DuaEntity> {
        val all = ArrayList<DuaEntity>(340)
        readInChunks(context, chunkSize = 40).forEach { chunk ->
            all.addAll(chunk)
        }
        return all
    }
}
