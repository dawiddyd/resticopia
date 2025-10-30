package org.dydlakcloud.resticopia.ui.folder

import android.app.AlertDialog
import android.os.Bundle
import android.view.*
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import org.dydlakcloud.resticopia.BackupManager
import org.dydlakcloud.resticopia.R
import org.dydlakcloud.resticopia.config.FolderConfigId
import org.dydlakcloud.resticopia.databinding.FragmentFolderBinding
import org.dydlakcloud.resticopia.restic.ResticSnapshotId
import org.dydlakcloud.resticopia.ui.Formatters
import org.dydlakcloud.resticopia.ui.snapshot.SnapshotActivity
import java.util.concurrent.CompletionException
import kotlin.math.roundToInt

class FolderFragment : Fragment() {
    private var _binding: FragmentFolderBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var _backupManager: BackupManager? = null
    private val backupManager get() = _backupManager!!

    private lateinit var _folderId: FolderConfigId
    private val folderId: FolderConfigId get() = _folderId

    private var snapshotIds: List<ResticSnapshotId>? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFolderBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setHasOptionsMenu(true)

        _backupManager = BackupManager.instance(requireContext())

        _folderId = (requireActivity() as FolderActivity).folderId
        val config = backupManager.config
        val folder = config.folders.find { it.id == folderId }
        val repo = folder?.repo(config)

        // fix nested scrolling for ListView
        binding.listFolderSnapshots.setOnTouchListener { view, event ->
            val action = event.action
            when (action) {
                MotionEvent.ACTION_DOWN ->
                    view.parent.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP ->
                    view.parent.requestDisallowInterceptTouchEvent(false)
            }

            view.onTouchEvent(event)
            true
        }

        if (folder != null && repo != null) {
            binding.textFolderName.text = folder.path.name
            binding.textFolderPath.text = folder.path.path
            
            // Combine schedule and retain info into one line
            val retainText = listOf(
                "Everything",
                listOf(
                    if (folder.keepLast == null) "" else "in last ${folder.keepLast}",
                    if (folder.keepWithin == null) "" else "within ${
                        Formatters.durationDaysHours(
                            folder.keepWithin
                        )
                    }"
                ).filter { it.isNotEmpty() }.joinToString(" and ")
            ).filter { it.isNotEmpty() }.joinToString(" ")
            
            binding.textScheduleRetain.text = "Schedule: ${folder.schedule} Retain: $retainText"

            val resticRepo = repo.repo(backupManager.restic)

            backupManager.observeConfig(viewLifecycleOwner) { config ->
                val folder = config.folders.find { it.id == folderId }

                val lastSuccessfulBackup = folder?.lastBackup(filterSuccessful = true)

                binding.textLastBackup.text =
                    if (lastSuccessfulBackup == null) ""
                    else {
                        val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm MMM dd, yyyy")
                        val formattedDate = lastSuccessfulBackup.timestamp.format(dateFormatter)
                        "Last Backup on $formattedDate"
                    }

                resticRepo.snapshots(resticRepo.restic.hostname).handle { snapshots, throwable ->
                    requireActivity().runOnUiThread {
                        binding.skeletonFolderSnapshots.visibility = GONE

                        val snapshots =
                            if (folder != null)
                                snapshots?.filter { it.paths.contains(folder.path) }?.reversed()
                                    ?: emptyList()
                            else
                                emptyList()

                        snapshotIds = snapshots.map { it.id }
                        binding.listFolderSnapshots.adapter = SnapshotListAdapter(
                            requireContext(),
                            snapshots,
                            repo.base.name
                        )
                        
                        // Hide divider if there's only one snapshot
                        if (snapshots.size <= 1) {
                            binding.listFolderSnapshots.divider = null
                            binding.listFolderSnapshots.dividerHeight = 0
                        }

                        if (throwable != null) {
                            val throwable =
                                if (throwable is CompletionException && throwable.cause != null) throwable.cause!!
                                else throwable

                            binding.textError.text = throwable.message
                            binding.textError.visibility = VISIBLE
                        }
                    }
                }
            }

            val activeBackup = backupManager.activeBackup(folderId)
            activeBackup.observe(viewLifecycleOwner) { backup ->
                when {
                    backup.isStarting() -> {
                        // Starting state: show loading indicator, disable button
                        binding.progressBackupStarting.visibility = VISIBLE
                        binding.backupStatusContainer.visibility = GONE
                        binding.buttonBackup.isEnabled = false
                        binding.progressBackup.setProgress(0, true)
                    }
                    backup.inProgress && backup.progress != null -> {
                        // In progress state: show upload icon + progress info, disable button
                        binding.progressBackupStarting.visibility = GONE
                        binding.backupStatusContainer.visibility = VISIBLE
                        binding.backupStatusIcon.setImageResource(R.drawable.ic_backup_upload)
                        binding.buttonBackup.isEnabled = false
                        
                        // Update progress bar
                        binding.progressBackup.setProgress(
                            (backup.progress.percentDone100()).roundToInt(),
                            true
                        )
                        
                        // Update files count: "13 / 25 Files"
                        val filesText = if (backup.progress.total_files != null) {
                            "${backup.progress.files_done} / ${backup.progress.total_files} Files"
                        } else {
                            "${backup.progress.files_done} Files"
                        }
                        binding.textBackupFiles.text = filesText
                        
                        // Update data size: "7.83MB / 56.23MB"
                        val sizeText = if (backup.progress.total_bytes != null) {
                            "${backup.progress.bytesDoneString()} / ${backup.progress.totalBytesString()}"
                        } else {
                            backup.progress.bytesDoneString()
                        }
                        binding.textBackupSize.text = sizeText
                        
                        binding.textBackupError.visibility = GONE
                    }
                    backup.error != null -> {
                        // Error state: show error message, enable button, hide status, reset progress
                        binding.progressBackupStarting.visibility = GONE
                        binding.backupStatusContainer.visibility = GONE
                        binding.buttonBackup.isEnabled = true
                        binding.progressBackup.setProgress(0, true)
                        
                        binding.textBackupError.text = backup.error
                        binding.textBackupError.visibility = VISIBLE
                        binding.textBackupError.setOnClickListener {
                            AlertDialog.Builder(context)
                                .setTitle("Backup Error")
                                .setMessage(backup.error)
                                .setPositiveButton("OK") { dialog, _ ->
                                    dialog.cancel()
                                }
                                .show()
                        }
                    }
                    backup.summary != null && backup.progress != null -> {
                        // Completed state: show checkmark icon + final counts, enable button
                        binding.progressBackupStarting.visibility = GONE
                        binding.backupStatusContainer.visibility = VISIBLE
                        binding.backupStatusIcon.setImageResource(R.drawable.ic_backup_complete)
                        binding.buttonBackup.isEnabled = true
                        
                        // Set progress to 100%
                        binding.progressBackup.setProgress(100, true)
                        
                        // Show final counts (e.g., "15 / 15 Files")
                        val filesText = if (backup.progress.total_files != null) {
                            "${backup.progress.total_files} / ${backup.progress.total_files} Files"
                        } else {
                            "${backup.progress.files_done} Files"
                        }
                        binding.textBackupFiles.text = filesText
                        
                        // Show final size (e.g., "56.23MB / 56.23MB")
                        val sizeText = if (backup.progress.total_bytes != null) {
                            "${backup.progress.totalBytesString()} / ${backup.progress.totalBytesString()}"
                        } else {
                            backup.progress.bytesDoneString()
                        }
                        binding.textBackupSize.text = sizeText
                        
                        binding.textBackupError.visibility = GONE
                    }
                    else -> {
                        // Idle state: hide status info, enable button, reset progress
                        binding.progressBackupStarting.visibility = GONE
                        binding.backupStatusContainer.visibility = GONE
                        binding.buttonBackup.isEnabled = true
                        binding.progressBackup.setProgress(0, true)
                        binding.textBackupError.visibility = GONE
                    }
                }
            }

            binding.listFolderSnapshots.setOnItemClickListener { _, _, position, _ ->
                val snapshotId = snapshotIds?.get(position)
                if (snapshotId != null) SnapshotActivity.start(this, folder.repoId, snapshotId)
            }

            binding.buttonBackup.setOnClickListener { _ ->
                backupManager.backup(
                    requireContext(),
                    folder,
                    removeOld = false,
                    scheduled = false
                )
            }

            // Cancel functionality moved to long press on button
            binding.buttonBackup.setOnLongClickListener { _ ->
                if (activeBackup.value?.inProgress == true) {
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.alert_backup_cancel_title)
                        .setMessage(R.string.alert_backup_cancel_message)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            activeBackup.value?.cancel()
                        }
                        .setNegativeButton(android.R.string.cancel) { _, _ -> }
                        .show()
                    true
                } else {
                    false
                }
            }
        }

        return root
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.nav_menu_entry, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_delete -> {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.alert_delete_folder_title)
                    .setMessage(R.string.alert_delete_folder_message)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        backupManager.configure { config ->
                            config.copy(folders = config.folders.filterNot { it.id == folderId })
                        }

                        requireActivity().finish()
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> }
                    .show()
                true
            }
            R.id.action_edit -> {
                FolderActivity.start(this, true, folderId)

                requireActivity().finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    override fun onDestroyView() {
        super.onDestroyView()
        _backupManager = null
        _binding = null
    }
}