package de.lolhens.resticui.ui.repos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import de.lolhens.resticui.BackupManager
import de.lolhens.resticui.config.RepoConfigId
import de.lolhens.resticui.databinding.FragmentReposBinding
import de.lolhens.resticui.ui.repo.RepoActivity

class ReposFragment : Fragment() {
    private var _binding: FragmentReposBinding? = null

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
        _binding = FragmentReposBinding.inflate(inflater, container, false)
        val root: View = binding.root

        _backupManager = BackupManager.instance(requireContext())

        backupManager.observeConfig(viewLifecycleOwner) { config ->
            binding.listRepos.adapter = RepoListAdapter(
                requireContext(),
                config.repos
            )
        }

        binding.fabReposAdd.setOnClickListener { _ ->
            RepoActivity.start(this, true, RepoConfigId.create())
        }

        binding.listRepos.setOnItemClickListener { _, _, position, _ ->
            val repo = backupManager.config.repos.get(position)
            RepoActivity.start(this, false, repo.base.id)
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _backupManager = null
        _binding = null
    }
}