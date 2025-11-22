package org.dydlakcloud.resticopia.ui.folder

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.fragment.app.Fragment
import org.dydlakcloud.resticopia.BackupManager
import org.dydlakcloud.resticopia.R
import org.dydlakcloud.resticopia.config.FolderConfig
import org.dydlakcloud.resticopia.config.FolderConfigId
import org.dydlakcloud.resticopia.databinding.FragmentFolderEditBinding
import org.dydlakcloud.resticopia.ui.Formatters
import org.dydlakcloud.resticopia.util.DirectoryChooser
import java.io.File
import java.time.Duration

class FolderEditFragment : Fragment() {
    companion object {
        val schedules = arrayOf(
            Pair("Manual", -1),
            Pair("Hourly", 60),
            Pair("Daily", 24 * 60),
            Pair("Weekly", 7 * 24 * 60),
            Pair("Monthly", 30 * 24 * 60)
        )

        val retainProfiles = arrayOf(
            -1,
            1,
            2,
            6,
            1 * 24,
            3 * 24,
            5 * 24,
            10 * 24,
            30 * 24,
            60 * 24,
            90 * 24,
            120 * 24,
            365 * 24
        )
    }

    private var _binding: FragmentFolderEditBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var _backupManager: BackupManager? = null
    private val backupManager get() = _backupManager!!

    private lateinit var _folderId: FolderConfigId
    private val folderId: FolderConfigId get() = _folderId

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFolderEditBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setHasOptionsMenu(true)

        _backupManager = BackupManager.instance(requireContext())

        _folderId = (requireActivity() as FolderActivity).folderId
        val config = backupManager.config
        val folder = config.folders.find { it.id == folderId }
        val folderRepo = folder?.repo(config)

        binding.spinnerRepo.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            backupManager.config.repos.map { it.base.name }
        )

        binding.spinnerSchedule.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            schedules.map { it.first }
        )
        binding.spinnerSchedule.setSelection(1)

        binding.spinnerRetainWithin.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            retainProfiles.map { hours ->
                if (hours == -1) "Always" else Formatters.durationDaysHours(
                    Duration.ofHours(hours.toLong())
                )
            }
        )
        binding.spinnerRetainWithin.setSelection(0)

        val directoryChooser = DirectoryChooser.newInstance()

        directoryChooser.register(this, requireContext()) { path ->
            binding.editFolder.setText(path)
        }

        binding.buttonFolderSelect.setOnClickListener {
            directoryChooser.openDialog()
        }

        binding.switchDeleteAfterBackup.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Show confirmation dialog when enabling
                showDeleteAfterBackupConfirmationDialog()
            } else {
                // Allow disabling without confirmation
                showToast(getString(R.string.toast_delete_after_backup_disabled))
            }
        }

        if (folder != null && folderRepo != null) {
            binding.spinnerRepo.setSelection(backupManager.config.repos.indexOfFirst { it.base.id == folderRepo.base.id })
            binding.editFolder.setText(folder.path.path)
            binding.spinnerSchedule.setSelection(schedules.indexOfFirst { it.first == folder.schedule })
            val scheduleIndex = retainProfiles.indexOfFirst {
                it.toLong() == folder.keepWithin?.toHours()
            }
            binding.spinnerRetainWithin.setSelection(if (scheduleIndex == -1) 0 else scheduleIndex)
            binding.switchDeleteAfterBackup.isChecked = folder.deleteContentsAfterBackup
        }

        return root
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.nav_menu_entry_edit, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_done -> {
                val selectedRepoName = binding.spinnerRepo.selectedItem?.toString()
                val repo =
                    if (selectedRepoName == null) null
                    else backupManager.config.repos.find { it.base.name == selectedRepoName }
                val path = binding.editFolder.text.toString()
                val schedule = binding.spinnerSchedule.selectedItem?.toString()
                val keepWithin =
                    if (retainProfiles[binding.spinnerRetainWithin.selectedItemPosition] < 0) null
                    else Duration.ofHours(retainProfiles[binding.spinnerRetainWithin.selectedItemPosition].toLong())

                if (
                    repo != null &&
                    path.isNotEmpty() &&
                    schedule != null
                ) {
                    val prevFolder = backupManager.config.folders.find { it.id == folderId }

                    val deleteContentsAfterBackup = binding.switchDeleteAfterBackup.isChecked

                    val folder = FolderConfig(
                        folderId,
                        repo.base.id,
                        File(path),
                        schedule,
                        prevFolder?.keepLast,
                        keepWithin,
                        deleteContentsAfterBackup,
                        prevFolder?.history ?: emptyList()
                    )

                    backupManager.configure { config ->
                        config.copy(folders = config.folders.filterNot { it.id == folderId }
                            .plus(folder))
                    }

                    FolderActivity.start(this, false, folderId)

                    requireActivity().finish()

                    true
                } else {
                    false
                }
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun showDeleteAfterBackupConfirmationDialog() {
        val editText = EditText(requireContext()).apply {
            hint = getString(R.string.alert_enable_delete_after_backup_hint)
            setSingleLine()
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.alert_enable_delete_after_backup_title)
            .setMessage(R.string.alert_enable_delete_after_backup_message)
            .setView(editText)
            .setPositiveButton(R.string.button_ok, null) // Set to null to override later
            .setNegativeButton(R.string.button_cancel) { _, _ ->
                binding.switchDeleteAfterBackup.isChecked = false
            }
            .setOnCancelListener {
                binding.switchDeleteAfterBackup.isChecked = false
            }
            .create()

        // Override the positive button to add validation
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.isEnabled = false

            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val text = s?.toString()?.trim() ?: ""
                    positiveButton.isEnabled = text == getString(R.string.alert_enable_delete_after_backup_confirm_text)
                    if (text.isNotEmpty() && text != getString(R.string.alert_enable_delete_after_backup_confirm_text)) {
                        editText.error = getString(R.string.alert_enable_delete_after_backup_wrong_text)
                    } else {
                        editText.error = null
                    }
                }
            })

            positiveButton.setOnClickListener {
                val text = editText.text.toString().trim()
                if (text == getString(R.string.alert_enable_delete_after_backup_confirm_text)) {
                    showToast(getString(R.string.toast_delete_after_backup_enabled))
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _backupManager = null
        _binding = null
    }
}