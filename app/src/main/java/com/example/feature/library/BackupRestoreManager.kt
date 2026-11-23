package com.example.feature.library

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.example.core.database.NoorDatabase
import com.example.core.database.UserBookmarkEntity
import com.example.core.database.UserNoteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object BackupRestoreManager {

    suspend fun exportUserDataToJson(context: Context, database: NoorDatabase, destinationUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val allBookmarks = database.openHelper.readableDatabase.query("SELECT * FROM user_bookmarks").let { cursor ->
                val list = mutableListOf<JSONObject>()
                while (cursor.moveToNext()) {
                    list.add(
                        JSONObject().apply {
                            put("id", cursor.getString(cursor.getColumnIndexOrThrow("id")))
                            put("verseKey", cursor.getString(cursor.getColumnIndexOrThrow("verseKey")))
                            put("surahId", cursor.getInt(cursor.getColumnIndexOrThrow("surahId")))
                            put("ayahNumber", cursor.getInt(cursor.getColumnIndexOrThrow("ayahNumber")))
                            put("collectionId", cursor.getString(cursor.getColumnIndexOrThrow("collectionId")))
                            put("label", cursor.getString(cursor.getColumnIndexOrThrow("label")))
                        }
                    )
                }
                cursor.close()
                list
            }

            val allNotes = database.openHelper.readableDatabase.query("SELECT * FROM user_notes").let { cursor ->
                val list = mutableListOf<JSONObject>()
                while (cursor.moveToNext()) {
                    list.add(
                        JSONObject().apply {
                            put("id", cursor.getString(cursor.getColumnIndexOrThrow("id")))
                            put("verseKey", cursor.getString(cursor.getColumnIndexOrThrow("verseKey")))
                            put("surahId", cursor.getInt(cursor.getColumnIndexOrThrow("surahId")))
                            put("ayahNumber", cursor.getInt(cursor.getColumnIndexOrThrow("ayahNumber")))
                            put("noteText", cursor.getString(cursor.getColumnIndexOrThrow("noteText")))
                        }
                    )
                }
                cursor.close()
                list
            }

            val rootJson = JSONObject().apply {
                put("app", "Al-Quran Backup")
                put("version", 1)
                put("timestamp", System.currentTimeMillis())
                put("bookmarks", JSONArray(allBookmarks))
                put("notes", JSONArray(allNotes))
            }

            context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                outputStream.write(rootJson.toString(2).toByteArray(Charsets.UTF_8))
            } ?: return@withContext Result.failure(IllegalStateException("Could not open output stream"))

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("BackupRestore", "Export failed", e)
            Result.failure(e)
        }
    }

    suspend fun importUserDataFromJson(context: Context, database: NoorDatabase, sourceUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(sourceUri)?.bufferedReader()?.use { it.readText() }
                ?: return@withContext Result.failure(IllegalStateException("Could not open input stream"))

            val root = JSONObject(jsonString)
            val bookmarksArray = root.optJSONArray("bookmarks") ?: JSONArray()
            val notesArray = root.optJSONArray("notes") ?: JSONArray()

            val dao = database.userLibraryDao()
            var importedCount = 0

            database.withTransaction {
                for (i in 0 until bookmarksArray.length()) {
                    val obj = bookmarksArray.getJSONObject(i)
                    val bm = UserBookmarkEntity(
                        id = obj.getString("id"),
                        verseKey = obj.getString("verseKey"),
                        surahId = obj.getInt("surahId"),
                        ayahNumber = obj.getInt("ayahNumber"),
                        collectionId = obj.optString("collectionId", "favorites"),
                        label = obj.optString("label"),
                        createdAtEpochMs = System.currentTimeMillis(),
                        updatedAtEpochMs = System.currentTimeMillis()
                    )
                    dao.insertBookmark(bm)
                    importedCount++
                }

                for (i in 0 until notesArray.length()) {
                    val obj = notesArray.getJSONObject(i)
                    val note = UserNoteEntity(
                        id = obj.getString("id"),
                        verseKey = obj.getString("verseKey"),
                        surahId = obj.getInt("surahId"),
                        ayahNumber = obj.getInt("ayahNumber"),
                        noteText = obj.getString("noteText"),
                        createdAtEpochMs = System.currentTimeMillis(),
                        updatedAtEpochMs = System.currentTimeMillis()
                    )
                    dao.insertNote(note)
                    importedCount++
                }
            }

            Result.success(importedCount)
        } catch (e: Exception) {
            Log.e("BackupRestore", "Import failed", e)
            Result.failure(e)
        }
    }
}
