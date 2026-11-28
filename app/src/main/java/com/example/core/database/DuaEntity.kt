package com.example.core.database

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Immutable
@Entity(tableName = "duas", indices = [Index("category"), Index("isBookmarked")])
data class DuaEntity(
    @PrimaryKey val id: Int,
    val category: String,
    val title: String,
    val arabicText: String,
    val transliteration: String,
    val translation: String,
    val reference: String,
    val virtueOrContext: String,
    val audioUrl: String,
    val isBookmarked: Boolean = false
)
