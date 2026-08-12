package code.name.monkey.retromusic.backup

import android.util.Log
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import code.name.monkey.retromusic.db.RetroDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupManager(
    private val context: Context,
    private val database: RetroDatabase
) {

    private val backupDir = File(Environment.getExternalStorageDirectory(), "MetroBackup")

    suspend fun exportBackup(): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!backupDir.exists()) backupDir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outFile = File(backupDir, "metro_backup_$timestamp.zip")

            checkpointDatabase()

            ZipOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { zip ->
                addFileToZip(zip, context.getDatabasePath("playlist.db"), "db/playlist.db")

                val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
                prefsDir.listFiles()?.forEach { prefFile ->
                    addFileToZip(zip, prefFile, "shared_prefs/${prefFile.name}")
                }
            }

            Result.success(outFile)
        } catch (e: Exception) {
            Log.e("BackupManager", "Export failed", e)
            Result.failure(e)
        }
    }

    // Now closes the DB connection before overwriting — caller restarts the app afterward
    suspend fun importBackup(zipFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            database.close()

            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    val targetFile = when {
                        entry.name.startsWith("db/") ->
                            context.getDatabasePath(entry.name.removePrefix("db/"))
                        entry.name.startsWith("shared_prefs/") ->
                            File(context.applicationInfo.dataDir, "shared_prefs/${entry.name.removePrefix("shared_prefs/")}")
                        else -> null
                    }
                    targetFile?.let {
                        it.parentFile?.mkdirs()
                        FileOutputStream(it).use { out -> zip.copyTo(out) }
                    }
                    entry = zip.nextEntry
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun listBackups(): List<File> =
        backupDir.listFiles { f -> f.extension == "zip" }?.sortedByDescending { it.lastModified() } ?: emptyList()

    private fun checkpointDatabase() {
        val dbFile = context.getDatabasePath("playlist.db")
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { c -> c.moveToFirst() }
        }
    }

    private fun addFileToZip(zip: ZipOutputStream, file: File, entryName: String) {
        if (!file.exists()) return
        zip.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { it.copyTo(zip) }
        zip.closeEntry()
    }
}
