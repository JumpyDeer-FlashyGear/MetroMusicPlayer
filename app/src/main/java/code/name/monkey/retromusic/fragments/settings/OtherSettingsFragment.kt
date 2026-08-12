package code.name.monkey.retromusic.fragments.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import code.name.monkey.appthemehelper.common.prefs.supportv7.ATEListPreference
import code.name.monkey.retromusic.LANGUAGE_NAME
import code.name.monkey.retromusic.LAST_ADDED_CUTOFF
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.backup.BackupManager
import code.name.monkey.retromusic.fragments.LibraryViewModel
import code.name.monkey.retromusic.fragments.ReloadType.HomeSections
import code.name.monkey.retromusic.util.PreferenceUtil
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import java.io.File

/**
 * @author Hemanth S (h4h13).
 */

class OtherSettingsFragment : AbsSettingsFragment() {
    private val libraryViewModel by activityViewModel<LibraryViewModel>()
    private val backupManager: BackupManager by inject()

    override fun invalidateSettings() {
        val languagePreference: ATEListPreference? = findPreference(LANGUAGE_NAME)
        languagePreference?.setOnPreferenceChangeListener { _, _ ->
            restartActivity()
            return@setOnPreferenceChangeListener true
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        PreferenceUtil.languageCode =
            AppCompatDelegate.getApplicationLocales().toLanguageTags().ifEmpty { "auto" }
        addPreferencesFromResource(R.xml.pref_advanced)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val preference: Preference? = findPreference(LAST_ADDED_CUTOFF)
        preference?.setOnPreferenceChangeListener { lastAdded, newValue ->
            setSummary(lastAdded, newValue)
            libraryViewModel.forceReload(HomeSections)
            true
        }
        val languagePreference: Preference? = findPreference(LANGUAGE_NAME)
        languagePreference?.setOnPreferenceChangeListener { prefs, newValue ->
            setSummary(prefs, newValue)
            if (newValue as? String == "auto") {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            } else {
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(
                        newValue as? String
                    )
                )
            }
            true
        }

        findPreference<Preference>("export_backup")?.setOnPreferenceClickListener {
            if (!hasStoragePermission()) {
                requestStoragePermission()
                return@setOnPreferenceClickListener true
            }
            lifecycleScope.launch {
                val result = backupManager.exportBackup()
                result.onSuccess { file ->
                    Toast.makeText(requireContext(), "Backup saved: ${file.name}", Toast.LENGTH_LONG).show()
                }.onFailure {
                    Toast.makeText(requireContext(), "Backup failed: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
            true
        }

        findPreference<Preference>("import_backup")?.setOnPreferenceClickListener {
            if (!hasStoragePermission()) {
                requestStoragePermission()
                return@setOnPreferenceClickListener true
            }
            val backups = backupManager.listBackups()
            if (backups.isEmpty()) {
                Toast.makeText(requireContext(), "No backups found", Toast.LENGTH_SHORT).show()
                return@setOnPreferenceClickListener true
            }
            AlertDialog.Builder(requireContext())
                .setTitle("Restore backup")
                .setItems(backups.map { it.name }.toTypedArray()) { _, which ->
                    confirmAndRestore(backups[which])
                }
                .show()
            true
        }
    }

    private fun confirmAndRestore(file: File) {
        AlertDialog.Builder(requireContext())
            .setTitle("Restore this backup?")
            .setMessage("This will overwrite your current data. The app will restart.")
            .setPositiveButton("Restore") { _, _ ->
                lifecycleScope.launch {
                    backupManager.importBackup(file)
                    restartApp()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun restartApp() {
        val intent = requireContext().packageManager.getLaunchIntentForPackage(requireContext().packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else true
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${requireContext().packageName}")
            }
            startActivity(intent)
            Toast.makeText(requireContext(), "Enable 'All files access' then try again", Toast.LENGTH_LONG).show()
        }
    }
}
