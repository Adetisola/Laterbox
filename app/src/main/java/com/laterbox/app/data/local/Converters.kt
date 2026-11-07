package com.laterbox.app.data.local

import androidx.room.TypeConverter
import com.laterbox.app.domain.model.ContentType
import com.laterbox.app.domain.model.MetadataStatus

class Converters {

    @TypeConverter
    fun fromContentType(value: ContentType): String {
        return value.name
    }

    @TypeConverter
    fun toContentType(value: String): ContentType {
        return try {
            ContentType.valueOf(value)
        } catch (e: Exception) {
            ContentType.WEBSITE
        }
    }

    @TypeConverter
    fun fromMetadataStatus(value: MetadataStatus): String {
        return value.name
    }

    @TypeConverter
    fun toMetadataStatus(value: String): MetadataStatus {
        return try {
            MetadataStatus.valueOf(value)
        } catch (e: Exception) {
            MetadataStatus.PENDING
        }
    }
}
