package com.gps.wareply.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A locally encrypted-by-Android-sandbox history used for both search and prompt memory. */
@Entity(tableName = "conversation_entries")
data class ConversationEntry(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactKey: String,
    val contactName: String,
    val incoming: String,
    val reply: String,
    val createdAt: Long,
    val automatic: Boolean,
    val delivered: Boolean,
    val aiWritten: Boolean,
    val skippedReason: String? = null
)

@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ConversationEntry)

    @Query("SELECT * FROM conversation_entries WHERE contactKey = :contactKey ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentForContact(contactKey: String, limit: Int): List<ConversationEntry>

    @Query("SELECT * FROM conversation_entries WHERE automatic = 0 AND delivered = 1 AND incoming != '' AND reply != '' ORDER BY createdAt DESC LIMIT :limit")
    suspend fun manualReplies(limit: Int): List<ConversationEntry>

    @Query("SELECT * FROM conversation_entries WHERE contactName LIKE '%' || :query || '%' OR incoming LIKE '%' || :query || '%' OR reply LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun search(query: String): Flow<List<ConversationEntry>>

    @Query("DELETE FROM conversation_entries WHERE contactKey = :contactKey")
    suspend fun deleteContact(contactKey: String)

    @Query("DELETE FROM conversation_entries")
    suspend fun deleteAll()
}

@Database(entities = [ConversationEntry::class], version = 1, exportSchema = false)
abstract class ConversationDatabase : RoomDatabase() {
    abstract fun conversations(): ConversationDao
}

/**
 * The app never reads WhatsApp's database. This stores only notification text
 * and replies generated/sent by this app in its own private Room database.
 */
object ConversationStore {
    @Volatile private var database: ConversationDatabase? = null

    fun init(context: Context) { db(context) }

    private fun db(context: Context): ConversationDatabase = database ?: synchronized(this) {
        database ?: Room.databaseBuilder(
            context.applicationContext, ConversationDatabase::class.java, "wa_reply_history.db"
        ).fallbackToDestructiveMigration().build().also { database = it }
    }

    suspend fun record(context: Context, entry: ConversationEntry) = db(context).conversations().insert(entry)

    suspend fun memory(context: Context, contactKey: String): List<ConversationEntry> =
        db(context).conversations().recentForContact(contactKey, 6).asReversed()

    suspend fun manualReplies(context: Context, limit: Int): List<ConversationEntry> =
        db(context).conversations().manualReplies(limit)

    fun history(context: Context, query: String): Flow<List<ConversationEntry>> =
        db(context).conversations().search(query.trim()).map { it.take(500) }

    suspend fun deleteContact(context: Context, contactKey: String) =
        db(context).conversations().deleteContact(contactKey)

    suspend fun deleteAll(context: Context) = db(context).conversations().deleteAll()
}
