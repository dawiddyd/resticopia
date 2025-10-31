package org.dydlakcloud.resticopia.ui.repo

import android.app.AlertDialog
import android.os.Bundle
import android.view.*
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import org.dydlakcloud.resticopia.BackupManager
import org.dydlakcloud.resticopia.R
import org.dydlakcloud.resticopia.config.RepoConfigId
import org.dydlakcloud.resticopia.databinding.FragmentRepoBinding
import org.dydlakcloud.resticopia.restic.ResticSnapshotId
import org.dydlakcloud.resticopia.ui.snapshot.SnapshotActivity
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletionException

class RepoFragment : Fragment() {
    private var _binding: FragmentRepoBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var _backupManager: BackupManager? = null
    private val backupManager get() = _backupManager!!

    private lateinit var _repoId: RepoConfigId
    private val repoId: RepoConfigId get() = _repoId

    private var snapshotIds: List<ResticSnapshotId>? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRepoBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setHasOptionsMenu(true)

        _backupManager = BackupManager.instance(requireContext())

        _repoId = (requireActivity() as RepoActivity).repoId
        val repo = backupManager.config.repos.find { it.base.id == repoId }

        if (repo != null) {
            binding.textRepoName.text = repo.base.name

            val resticRepo = repo.repo(backupManager.restic)

            binding.textRepoUrl.text = resticRepo.repository()

            backupManager.observeConfig(viewLifecycleOwner) { _ ->
                resticRepo.snapshots().handle { snapshots, throwable ->
                    requireActivity().runOnUiThread {
                        binding.skeletonRepoSnapshots.visibility = GONE

                        val snapshots = snapshots?.reversed() ?: emptyList()

                        snapshotIds = snapshots.map { it.id }
                        binding.listRepoSnapshots.adapter = RepoSnapshotListAdapter(
                            requireContext(),
                            snapshots
                        )
                        
                        // Hide divider if there's only one snapshot
                        if (snapshots.size <= 1) {
                            binding.listRepoSnapshots.divider = null
                            binding.listRepoSnapshots.dividerHeight = 0
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
        }

        binding.listRepoSnapshots.setOnItemClickListener { _, _, position, _ ->
            val snapshotId = snapshotIds?.get(position)
            if (snapshotId != null) SnapshotActivity.start(this, repoId, snapshotId)
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
                    .setTitle(R.string.alert_delete_repo_title)
                    .setMessage(R.string.alert_delete_repo_message)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        backupManager.configure { config ->
                            config.copy(repos = config.repos.filterNot { it.base.id == repoId })
                        }

                        requireActivity().finish()
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> }
                    .show()
                true
            }
            R.id.action_edit -> {
                RepoActivity.start(this, true, repoId)

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