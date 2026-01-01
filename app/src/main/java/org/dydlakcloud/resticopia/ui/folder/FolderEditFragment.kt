package org.dydlakcloud.resticopia.ui.folder

import android.os.Bundle
import android.view.*
import android.widget.ArrayAdapter
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

        binding.spinnerRepo.setAdapter(ArrayAdapter(
            requireContext(),
            R.layout.material3_dropdown_item,
            backupManager.config.repos.map { it.base.name }
        ))

        binding.spinnerSchedule.setAdapter(ArrayAdapter(
            requireContext(),
            R.layout.material3_dropdown_item,
            schedules.map { it.first }
        ))
        binding.spinnerSchedule.setText(schedules[1].first, false)

        binding.spinnerRetainWithin.setAdapter(ArrayAdapter(
            requireContext(),
            R.layout.material3_dropdown_item,
            retainProfiles.map { hours ->
                if (hours == -1) "Always" else Formatters.durationDaysHours(
                    Duration.ofHours(hours.toLong())
                )
            }
        ))
        binding.spinnerRetainWithin.setText("Always", false)

        val directoryChooser = DirectoryChooser.newInstance()

        directoryChooser.register(this, requireContext()) { path ->
            binding.editFolder.setText(path)
        }

        binding.textInputFolder.setEndIconOnClickListener {
            directoryChooser.openDialog()
        }

        if (folder != null && folderRepo != null) {
            binding.spinnerRepo.setText(folderRepo.base.name, false)
            binding.editFolder.setText(folder.path.path)
            val scheduleText = schedules.find { it.first == folder.schedule }?.first ?: schedules[1].first
            binding.spinnerSchedule.setText(scheduleText, false)
            val scheduleIndex = retainProfiles.indexOfFirst {
                it.toLong() == folder.keepWithin?.toHours()
            }
            val retainText = if (scheduleIndex == -1) "Always" else {
                val hours = retainProfiles[scheduleIndex]
                if (hours == -1) "Always" else Formatters.durationDaysHours(Duration.ofHours(hours.toLong()))
            }
            binding.spinnerRetainWithin.setText(retainText, false)
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
                val selectedRepoName = binding.spinnerRepo.text.toString()
                val repo =
                    if (selectedRepoName.isEmpty()) null
                    else backupManager.config.repos.find { it.base.name == selectedRepoName }
                val path = binding.editFolder.text.toString()
                val schedule = binding.spinnerSchedule.text.toString()
                val retainText = binding.spinnerRetainWithin.text.toString()
                val retainIndex = retainProfiles.map { hours ->
                    if (hours == -1) "Always" else Formatters.durationDaysHours(Duration.ofHours(hours.toLong()))
                }.indexOf(retainText)
                val keepWithin =
                    if (retainIndex == -1 || retainProfiles[retainIndex] < 0) null
                    else Duration.ofHours(retainProfiles[retainIndex].toLong())

                if (
                    repo != null &&
                    path.isNotEmpty() &&
                    schedule != null
                ) {
                    val prevFolder = backupManager.config.folders.find { it.id == folderId }

                    val folder = FolderConfig(
                        folderId,
                        repo.base.id,
                        File(path),
                        schedule,
                        prevFolder?.keepLast,
                        keepWithin,
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

    override fun onDestroyView() {
        super.onDestroyView()
        _backupManager = null
        _binding = null
    }
}