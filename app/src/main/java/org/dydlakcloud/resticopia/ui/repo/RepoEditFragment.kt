package org.dydlakcloud.resticopia.ui.repo

import android.app.Activity.RESULT_OK
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.view.View.*
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import org.dydlakcloud.resticopia.BackupManager
import org.dydlakcloud.resticopia.R
import org.dydlakcloud.resticopia.config.*
import org.dydlakcloud.resticopia.databinding.FragmentRepoEditBinding
import org.dydlakcloud.resticopia.util.ErrorHandler
import org.dydlakcloud.resticopia.util.DirectoryChooser
import org.dydlakcloud.resticopia.util.RcloneConfigParser
import java.io.File
import java.net.URI
import java.util.concurrent.CompletionException


class RepoEditFragment : Fragment() {
    private var _binding: FragmentRepoEditBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var _backupManager: BackupManager? = null
    private val backupManager get() = _backupManager!!

    private lateinit var _repoId: RepoConfigId
    private val repoId: RepoConfigId get() = _repoId

    private val directoryChooser = DirectoryChooser.newInstance()
    
    // Rclone configuration
    private var rcloneRemotes: List<RcloneConfigParser.RcloneRemote> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRepoEditBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setHasOptionsMenu(true)

        _backupManager = BackupManager.instance(requireContext())

        _repoId = (requireActivity() as RepoActivity).repoId
        val repo = backupManager.config.repos.find { it.base.id == repoId }

        // Setup spinner with custom adapter that includes icons
        val repoTypeAdapter = RepoTypeSpinnerAdapter(
            requireContext(),
            RepoType.values()
        )
        binding.spinnerRepoType.setAdapter(repoTypeAdapter)

        // define a listener to change the repo param view based on which repo type is selected in the drop down
        binding.spinnerRepoType.setOnItemClickListener { parent, view, position, id ->
            val getRepoTypeBinding: (RepoType) -> ViewBinding = {
                when (it) {
                    RepoType.S3 -> binding.editRepoS3Parameters
                    RepoType.Rest -> binding.editRepoRestParameters
                    RepoType.B2 -> binding.editRepoB2Parameters
                    RepoType.Local -> binding.editRepoLocalParameters
                    RepoType.Rclone -> binding.editRepoRcloneParameters
                }
            }

            val newRepoType = parent!!.getItemAtPosition(position) as RepoType
            RepoType.values().forEach { repoType ->
                getRepoTypeBinding(repoType).root.visibility =
                    if (repoType == newRepoType) VISIBLE else GONE
            }
        }

        if (repo != null) {
            // Disable Spinner in Edit mode since it should not be possible to change a repos type after is has been
            // created
            binding.spinnerRepoType.isEnabled = false
            binding.spinnerRepoType.isClickable = false

            // prefill the view if the repo already exists and is going to be edited instead of created.
            binding.editRepoName.setText(repo.base.name)
            binding.editRepoPassword.setText(repo.base.password.secret)
            binding.spinnerRepoType.setText(repo.base.type.toString(), false)
            when (repo.base.type) {
                RepoType.S3 -> {
                    val s3RepoParams = repo.params as S3RepoParams
                    binding.editRepoS3Parameters.editS3Uri.setText(s3RepoParams.s3Url.toString())
                    binding.editRepoS3Parameters.editS3AccessKeyId.setText(s3RepoParams.accessKeyId)
                    binding.editRepoS3Parameters.editS3SecretAccessKey.setText(s3RepoParams.secretAccessKey.secret)
                    binding.editRepoS3Parameters.editS3DefaultRegion.setText(s3RepoParams.s3DefaultRegion.toString())
                }
                RepoType.Rest -> {
                    val restRepoParams = repo.params as RestRepoParams
                    binding.editRepoRestParameters.editRestUri.setText(restRepoParams.restUrl.toString())
                }
                RepoType.B2 -> {
                    val b2RepoParams = repo.params as B2RepoParams
                    binding.editRepoB2Parameters.editB2Uri.setText(b2RepoParams.b2Url.toString())
                    binding.editRepoB2Parameters.editB2AccountId.setText(b2RepoParams.b2AccountId)
                    binding.editRepoB2Parameters.editB2AccountKey.setText(b2RepoParams.b2AccountKey.secret)
                }
                RepoType.Local -> {
                    val localRepoParams = repo.params as LocalRepoParams
                    binding.editRepoLocalParameters.editLocalPath.setText(localRepoParams.localPath)
                }
                RepoType.Rclone -> {
                    val rcloneRepoParams = repo.params as RcloneRepoParams
                    binding.editRepoRcloneParameters.editRclonePath.setText(rcloneRepoParams.rclonePath)
                    // Remotes will be loaded in loadRcloneRemotes() after layout is set up
                }

            }.apply {} // do not remove - throws a compiler error if any of the repo types cases is not covered by the when
        }

        // Setup directory chooser for local repository
        directoryChooser.register(this, requireContext()) { path ->
            binding.editRepoLocalParameters.editLocalPath.setText(path)
        }

        // Setup browse button click listener
        binding.editRepoLocalParameters.buttonBrowseLocalPath.setOnClickListener {
            directoryChooser.openDialog()
        }
        
        // Load rclone remotes from global config
        loadRcloneRemotes()

        return root
    }
    
    /**
     * Load rclone remotes from global config and populate the remote selector
     */
    private fun loadRcloneRemotes() {
        val globalConfig = backupManager.config.rcloneConfig

        if (globalConfig.isNullOrBlank()) {
            // No global config - show warning
            binding.editRepoRcloneParameters.textRcloneConfigStatus.text =
                getString(R.string.repo_edit_rclone_config_not_configured)
            binding.editRepoRcloneParameters.spinnerRcloneRemote.isEnabled = false
            return
        }

        // Debug logging
        println("DEBUG: Global rclone config length: ${globalConfig.length}")
        println("DEBUG: Global rclone config preview: ${globalConfig.take(200)}")

        
        // Parse the global config
        try {
            rcloneRemotes = RcloneConfigParser.parseConfigContent(globalConfig)
        } catch (e: Exception) {
            binding.editRepoRcloneParameters.textRcloneConfigStatus.text = 
                getString(R.string.repo_edit_rclone_config_invalid)
            binding.editRepoRcloneParameters.spinnerRcloneRemote.isEnabled = false
            return
        }
        
        if (rcloneRemotes.isEmpty()) {
            binding.editRepoRcloneParameters.textRcloneConfigStatus.text = 
                getString(R.string.repo_edit_rclone_config_invalid)
            binding.editRepoRcloneParameters.spinnerRcloneRemote.isEnabled = false
            return
        }
        
        // Update UI with available remotes
        binding.editRepoRcloneParameters.textRcloneConfigStatus.text = 
            getString(R.string.repo_edit_rclone_config_available, rcloneRemotes.size)
        
        // Populate spinner with remotes
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            rcloneRemotes
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.editRepoRcloneParameters.spinnerRcloneRemote.setAdapter(adapter)
        binding.editRepoRcloneParameters.spinnerRcloneRemote.isEnabled = true
        
        // If editing an existing repo, try to find and select the remote from config
        val existingRepo = backupManager.config.repos.find { it.base.id == repoId }
        if (existingRepo?.params is RcloneRepoParams) {
            val remoteToSelect = (existingRepo.params as RcloneRepoParams).rcloneRemote
            val index = rcloneRemotes.indexOfFirst { it.name == remoteToSelect }
            if (index >= 0) {
                binding.editRepoRcloneParameters.spinnerRcloneRemote.setText(rcloneRemotes[index].name, false)
                println("DEBUG: Found existing remote '$remoteToSelect' at index $index")
            } else {
                // Remote not found in current config - show error
                binding.editRepoRcloneParameters.textRcloneConfigStatus.text =
                    getString(R.string.repo_edit_rclone_remote_not_found, remoteToSelect)
                binding.editRepoRcloneParameters.spinnerRcloneRemote.isEnabled = false
                println("DEBUG: Remote '$remoteToSelect' not found in current config")
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.nav_menu_entry_edit, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_done -> {
                val (valid, repo) = parseRepo()

                if (valid) {

                    fun saveRepo() {
                        backupManager.configure { config ->
                            config.copy(repos = config.repos.filterNot { it.base.id == repoId }
                                .plus(repo!!))
                        }

                        RepoActivity.start(this, false, repoId)

                        requireActivity().finish()
                    }

                    Toast.makeText(context, R.string.text_saving, Toast.LENGTH_SHORT).show()

                    item.isEnabled = false
                    binding.progressRepoSave.visibility = VISIBLE

                    val resticRepo = repo!!.repo(backupManager.restic)
                    resticRepo.stats().handle { _, throwable ->
                        requireActivity().runOnUiThread {
                            if (throwable == null) {
                                saveRepo()
                            } else {
                                // Unwrap CompletionException to get the actual cause
                                val actualThrowable =
                                    if (throwable is CompletionException && throwable.cause != null) throwable.cause!!
                                    else throwable

                                System.err.println("Error saving repository!")
                                actualThrowable.printStackTrace()

                                item.isEnabled = true
                                binding.progressRepoSave.visibility = INVISIBLE

                                val errorHandler = ErrorHandler(requireContext())
                                val userFriendlyError = errorHandler.getUserFriendlyError(actualThrowable)

                                // For repository not found errors, offer to initialize
                                val isRepositoryNotFound = userFriendlyError.category == ErrorHandler.ErrorCategory.REPOSITORY_NOT_FOUND

                                if (isRepositoryNotFound) {
                                    // Show initialization dialog with option to initialize repo
                                    AlertDialog.Builder(requireActivity())
                                        .setTitle(R.string.alert_init_repo_title)
                                        .setMessage(R.string.alert_init_repo_message)
                                        .setPositiveButton(android.R.string.ok) { _, _ ->
                                            item.isEnabled = false
                                            binding.progressRepoSave.visibility = VISIBLE

                                            resticRepo.init().handle { _, throwable ->
                                                requireActivity().runOnUiThread {
                                                    if (throwable == null) {
                                                        saveRepo()
                                                    } else {
                                                        val throwable =
                                                            if (throwable is CompletionException && throwable.cause != null) throwable.cause!!
                                                            else throwable

                                                        throwable.printStackTrace()

                                                        item.isEnabled = true
                                                        binding.progressRepoSave.visibility = INVISIBLE

                                                        val errorHandler = ErrorHandler(requireContext())
                                                        val userFriendlyError = errorHandler.getUserFriendlyError(throwable)

                                                        val errorMessage = buildString {
                                                            append(userFriendlyError.message)
                                                            if (userFriendlyError.suggestion != null) {
                                                                append("\n\n")
                                                                append(userFriendlyError.suggestion)
                                                            }
                                                        }

                                                        AlertDialog.Builder(requireActivity())
                                                            .setTitle(userFriendlyError.title)
                                                            .setMessage(errorMessage)
                                                            .setPositiveButton(android.R.string.ok, null) // Just close dialog
                                                            .setNeutralButton(R.string.error_show_technical_details) { _, _ ->
                                                                showTechnicalDetailsDialog(userFriendlyError)
                                                            }
                                                            .show()
                                                    }
                                                }
                                            }
                                        }
                                        .setNegativeButton(android.R.string.cancel) { _, _ -> }
                                        .show()
                                } else {
                                    // Show error dialog with technical details option
                                    val errorMessage = buildString {
                                        append(userFriendlyError.message)
                                        if (userFriendlyError.suggestion != null) {
                                            append("\n\n")
                                            append(userFriendlyError.suggestion)
                                        }
                                    }

                                    AlertDialog.Builder(requireActivity())
                                        .setTitle(userFriendlyError.title)
                                        .setMessage(errorMessage)
                                        .setPositiveButton(android.R.string.ok, null) // Just close dialog
                                        .setNeutralButton(R.string.error_show_technical_details) { _, _ ->
                                            showTechnicalDetailsDialog(userFriendlyError)
                                        }
                                        .show()
                                }
                            }
                        }
                    }

                    true
                } else {
                    false
                }
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun parseRepo(): Pair<Boolean, RepoConfig?> {
        val repoTypeText = binding.spinnerRepoType.text.toString()
        val repoType = RepoType.valueOf(repoTypeText)
        val valid = validateRepo(repoType)

        if (!valid) {
            return false to null
        }

        val baseConfig = RepoBaseConfig(
            id = repoId,
            name = binding.editRepoName.text.toString(),
            type = repoType,
            password = Secret(binding.editRepoPassword.text.toString())
        )

        return true to when (repoType) {
            RepoType.S3 -> {
                RepoConfig(
                    baseConfig,
                    S3RepoParams(
                        s3Url = URI(binding.editRepoS3Parameters.editS3Uri.text.toString()),
                        accessKeyId = binding.editRepoS3Parameters.editS3AccessKeyId.text.toString(),
                        secretAccessKey = Secret(binding.editRepoS3Parameters.editS3SecretAccessKey.text.toString()),
                        s3DefaultRegion = binding.editRepoS3Parameters.editS3DefaultRegion.text.toString()
                    )
                )
            }
            RepoType.Rest -> {
                RepoConfig(
                    baseConfig,
                    RestRepoParams(
                        restUrl = URI(binding.editRepoRestParameters.editRestUri.text.toString()),
                    )
                )
            }
            RepoType.B2 -> {
                RepoConfig(
                    baseConfig,
                    B2RepoParams(
                        b2Url = URI(binding.editRepoB2Parameters.editB2Uri.text.toString()),
                        b2AccountId = binding.editRepoB2Parameters.editB2AccountId.text.toString(),
                        b2AccountKey = Secret(binding.editRepoB2Parameters.editB2AccountKey.text.toString()),
                    )
                )
            }
            RepoType.Local -> {
                RepoConfig(
                    baseConfig,
                    LocalRepoParams(
                        localPath = binding.editRepoLocalParameters.editLocalPath.text.toString()
                    )
                )
            }
            RepoType.Rclone -> {
                val selectedRemoteName = binding.editRepoRcloneParameters.spinnerRcloneRemote.text.toString()
                val selectedRemote = rcloneRemotes.find { it.name == selectedRemoteName }
                val pathText = binding.editRepoRcloneParameters.editRclonePath.text.toString()
                RepoConfig(
                    baseConfig,
                    RcloneRepoParams(
                        rcloneRemote = selectedRemote?.name ?: "",
                        rclonePath = pathText.ifEmpty { "" } // Empty string means root directory
                    )
                )
            }
        }
    }

    private fun checkFieldMandatory(field: TextView, errorMessage: String): Boolean {
        if (field.text.toString().isEmpty()) {
            field.error = errorMessage
            return false
        }
        return true
    }

    private fun validateRepo(repoType: RepoType): Boolean {
        val baseValidatorResults = listOf(
            checkFieldMandatory(binding.editRepoName, getString(R.string.repo_edit_name_error_mandatory)),
            checkFieldMandatory(binding.editRepoPassword, getString(R.string.repo_edit_password_error_mandatory)),
        )

        val validatorResults = when (repoType) {
            RepoType.S3 -> {
                baseValidatorResults.plus(
                    listOf(
                        checkFieldMandatory(
                            binding.editRepoS3Parameters.editS3Uri,
                            getString(R.string.repo_edit_s3_uri_error_mandatory)
                        ),
                        checkFieldMandatory(
                            binding.editRepoS3Parameters.editS3AccessKeyId,
                            getString(R.string.repo_edit_s3_access_key_id_error_mandatory)
                        ),
                        checkFieldMandatory(
                            binding.editRepoS3Parameters.editS3SecretAccessKey,
                            getString(R.string.repo_edit_s3_secret_access_key_error_mandatory)
                        ),

                        )
                )
            }
            RepoType.Rest -> {
                baseValidatorResults.plus(
                    listOf(
                        checkFieldMandatory(
                            binding.editRepoRestParameters.editRestUri,
                            getString(R.string.repo_edit_rest_uri_error_mandatory)
                        ),

                        )
                )
            }
            RepoType.B2 -> {
                baseValidatorResults.plus(
                    listOf(
                        checkFieldMandatory(
                            binding.editRepoB2Parameters.editB2Uri,
                            getString(R.string.repo_edit_b2_uri_error_mandatory)
                        ),
                        checkFieldMandatory(
                            binding.editRepoB2Parameters.editB2AccountId,
                            getString(R.string.repo_edit_b2_account_id_error_mandatory)
                        ),
                        checkFieldMandatory(
                            binding.editRepoB2Parameters.editB2AccountKey,
                            getString(R.string.repo_edit_b2_account_key_error_mandatory)
                        ),

                        )
                )
            }
            RepoType.Local -> {
                baseValidatorResults.plus(
                    listOf(
                        checkFieldMandatory(
                            binding.editRepoLocalParameters.editLocalPath,
                            getString(R.string.repo_edit_local_path_error_mandatory)
                        )
                    )
                )
            }
            RepoType.Rclone -> {
                val hasRemote = binding.editRepoRcloneParameters.spinnerRcloneRemote.text.isNotEmpty()
                if (!hasRemote) {
                    Toast.makeText(context, R.string.repo_edit_rclone_remote_error_mandatory, Toast.LENGTH_SHORT).show()
                }
                baseValidatorResults.plus(
                    listOf(
                        hasRemote
                        // rclone path is now optional - empty path means root directory
                    )
                )
            }
        }

        return validatorResults.all { result -> result }
    }

    override fun onResume() {
        super.onResume()
        // Reload rclone remotes in case config was updated in Settings
        if (_binding != null) {
            loadRcloneRemotes()
        }
    }

    private fun showTechnicalDetailsDialog(userFriendlyError: ErrorHandler.UserFriendlyError) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.error_show_technical_details))
            .setMessage(userFriendlyError.originalError)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.button_copy) { _, _ ->
                // Copy technical details to clipboard
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Technical Error Details", userFriendlyError.originalError)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Technical details copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _backupManager = null
        _binding = null
    }
}
