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
            clearStaleWalFiles()

            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    // A zip entry named e.g. "db/" (no filename after the prefix) is a bare
                    // directory record, not a file. Our own exportBackup() never writes one -
                    // it only ever adds explicit file entries - but a zip that's been
                    // repackaged by some other tool along the way (a file manager's
                    // "compress", a cloud-sync re-zip, etc.) commonly does add these for every
                    // folder. Feeding an empty name into context.getDatabasePath("") throws
                    // StringIndexOutOfBoundsException: length=0; index=0 from deep inside
                    // ContextImpl, which used to be silently swallowed by this method's catch
                    // block - see the comment below. Skip directory entries, and skip any
                    // entry that resolves to a blank filename after stripping its prefix, so
                    // this can't happen regardless of how the zip was produced.
                    val currentEntry = entry
                    val strippedDbName = currentEntry.name.removePrefix("db/")
                    val strippedPrefsName = currentEntry.name.removePrefix("shared_prefs/")
                    val targetFile = when {
                        currentEntry.isDirectory -> null
                        currentEntry.name.startsWith("db/") && strippedDbName.isNotBlank() ->
                            context.getDatabasePath(strippedDbName)
                        currentEntry.name.startsWith("shared_prefs/") && strippedPrefsName.isNotBlank() ->
                            File(context.applicationInfo.dataDir, "shared_prefs/$strippedPrefsName")
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
            // Previously swallowed silently (Result.failure(e) with no log), which is why a
            // release-only failure here was indistinguishable from "did nothing": the caller
            // (OtherSettingsFragment.confirmAndRestore) used to ignore this Result and always
            // restart regardless of outcome. Logging here + honoring the Result on the caller
            // side (see that fragment) makes any future failure actually visible.
            Log.e("BackupManager", "Import failed", e)
            Result.failure(e)
        }
    }

    // Room defaults to WAL journal mode, so closing the database above does not guarantee
    // playlist.db-wal / playlist.db-shm are empty or absent. If either is left over from the
    // pre-restore session, SQLite can replay those stale WAL frames on top of the just-restored
    // main db file the next time it's opened, silently undoing (part of) the restore. The
    // exported backup only ever contains the checkpointed main file, so it's safe to drop any
    // leftover WAL/SHM files here before writing the restored main file in.
    private fun clearStaleWalFiles() {
        val dbFile = context.getDatabasePath("playlist.db")
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
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
