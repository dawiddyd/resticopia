package org.dydlakcloud.resticopia.ui.folders

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.dydlakcloud.resticopia.BackupManager
import org.dydlakcloud.resticopia.config.FolderConfigId
import org.dydlakcloud.resticopia.databinding.FragmentFoldersBinding
import org.dydlakcloud.resticopia.ui.folder.FolderActivity

class FoldersFragment : Fragment() {
    private var _binding: FragmentFoldersBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var _backupManager: BackupManager? = null
    private val backupManager get() = _backupManager!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFoldersBinding.inflate(inflater, container, false)
        val root: View = binding.root

        _backupManager = BackupManager.instance(requireContext())

        backupManager.observeConfig(viewLifecycleOwner) { config ->
            binding.listFolders.adapter = FolderListAdapter(
                requireContext(),
                config.folders,
                config
            )
        }

        binding.fabFoldersAdd.setOnClickListener { _ ->
            FolderActivity.start(this, true, FolderConfigId.create())
        }

        binding.listFolders.setOnItemClickListener { _, _, position, _ ->
            val folder = backupManager.config.folders.get(position)
            FolderActivity.start(this, false, folder.id)
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _backupManager = null
        _binding = null
    }
}