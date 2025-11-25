package org.dydlakcloud.resticopia.ui.snapshot

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.view.*
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import org.dydlakcloud.resticopia.BackupManager
import org.dydlakcloud.resticopia.R
import org.dydlakcloud.resticopia.config.RepoConfigId
import org.dydlakcloud.resticopia.databinding.FragmentSnapshotBinding
import org.dydlakcloud.resticopia.restic.ResticFile
import org.dydlakcloud.resticopia.restic.ResticRepo
import org.dydlakcloud.resticopia.restic.ResticSnapshotId
import org.dydlakcloud.resticopia.ui.Formatters
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SnapshotFragment : Fragment() {
    private var _binding: FragmentSnapshotBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var _backupManager: BackupManager? = null
    private val backupManager get() = _backupManager!!

    private lateinit var _repoId: RepoConfigId
    private val repoId: RepoConfigId get() = _repoId

    private lateinit var _snapshotId: ResticSnapshotId
    private val snapshotId: ResticSnapshotId get() = _snapshotId

    private var _snapshotRootPath: File? = null
    private val snapshotRootPath: File? get() = _snapshotRootPath

    private var _resticRepo: ResticRepo? = null
    private val resticRepo: ResticRepo? get() = _resticRepo

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSnapshotBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setHasOptionsMenu(true)

        _backupManager = BackupManager.instance(requireContext())

        val activity = requireActivity() as SnapshotActivity
        _repoId = activity.repoId
        _snapshotId = activity.snapshotId

        binding.textSnapshotId.text = snapshotId.short

        val repo = backupManager.config.repos.find { it.base.id == repoId }

        if (repo != null) {
            val resticRepo = repo.repo(backupManager.restic)
            _resticRepo = resticRepo

            resticRepo.cat(snapshotId).handle { snapshot, throwable ->
                requireActivity().runOnUiThread {
                    if (snapshot != null) {
                        val snapshotRootPath = snapshot.paths[0]
                        _snapshotRootPath = snapshotRootPath
                        
                        // Format date as "Created on HH:mm MMM dd, yyyy"
                        val timeString = "Created on ${Formatters.dateTimeDetailed(snapshot.time)}"

                        // Combine hostname and path on one line
                        binding.textHostnamePath.text = "${snapshot.hostname} ${snapshotRootPath.path}"
                        binding.textTime.text = timeString

                        // Setup Download All button
                        setupDownloadAllButton()

                        resticRepo.ls(snapshotId).handle { lsResult, throwable ->
                            requireActivity().runOnUiThread {
                                if (lsResult != null) {
                                    val (_, files) = lsResult
                                    binding.skeletonSnapshotFiles.visibility = GONE

                                    binding.listFilesSnapshot.adapter = SnapshotFilesListAdapter(
                                        requireContext(),
                                        ArrayList(
                                            files.filter {
                                                it.path.startsWith(snapshotRootPath) &&
                                                        it.path.relativeTo(snapshotRootPath).path.isNotEmpty() &&
                                                        it.type != "dir" // Exclude directories, show only files
                                            }
                                        ),
                                        resticRepo,
                                        snapshotId,
                                        snapshotRootPath,
                                        binding.progressDl
                                    )

                                    binding.listFilesSnapshot.onItemClickListener =
                                        AdapterView.OnItemClickListener { _, _, _, _ ->
                                            (binding.listFilesSnapshot.adapter as SnapshotFilesListAdapter)
                                                .triggerSort(binding.listFilesSnapshot)
                                        }
                                } else {
                                    throwable?.printStackTrace()
                                }
                            }
                        }
                    } else {
                        throwable?.printStackTrace()
                    }
                }
            }
        }

        return root
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.nav_menu_entry_delete, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_delete -> {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.alert_delete_snapshot_title)
                    .setMessage(R.string.alert_delete_snapshot_message)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        val repo = backupManager.config.repos.find { it.base.id == repoId }
                        if (repo != null) {
                            val resticRepo = repo.repo(backupManager.restic)

                            item.isEnabled = false
                            binding.progressSnapshotDelete.visibility = VISIBLE

                            resticRepo.forget(listOf(snapshotId), prune = true)
                                .handle { _, throwable ->
                                    if (throwable == null) {
                                        backupManager.configure { config ->
                                            config
                                        }
                                        requireActivity().finish()
                                    } else {
                                        throwable.printStackTrace()
                                    }
                                }
                        }
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> }
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    override fun onDestroyView() {
        super.onDestroyView()
        _backupManager = null
        _binding = null
        _snapshotRootPath = null
        _resticRepo = null
    }

    private fun setupDownloadAllButton() {
        binding.buttonDownloadAll.setOnClickListener {
            val sharedPref = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
            val downloadPathString = sharedPref?.getString("dl_path", "") ?: ""
            val downloadPath = File(downloadPathString)

            if (!(downloadPath.exists() && downloadPath.isDirectory)) {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.alert_download_all_title)
                    .setMessage(R.string.alert_download_file_no_dest_dir)
                    .setNegativeButton(android.R.string.cancel) { _, _ -> }
                    .show()
            } else {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.alert_download_all_title)
                    .setMessage(getString(R.string.alert_download_all_message, downloadPathString, snapshotId.short))
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        downloadAllFiles(downloadPath)
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> }
                    .show()
            }
        }
    }

    private fun downloadAllFiles(downloadPath: File) {
        val repo = resticRepo
        val rootPath = snapshotRootPath

        if (repo == null || rootPath == null) {
            Toast.makeText(requireContext(), R.string.toast_download_all_failed, Toast.LENGTH_LONG).show()
            return
        }

        binding.progressDl.visibility = VISIBLE
        binding.buttonDownloadAll.isEnabled = false

        // Create a temporary directory for restoration
        val tempDir = File(requireContext().cacheDir, "snapshot_restore_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        repo.restoreAll(snapshotId, tempDir, rootPath)
            .handle { content, throwable ->
                if (content != null) {
                    // Files restored successfully, now create ZIP
                    try {
                        val zipFileName = "snapshot_${snapshotId.short}.zip"
                        val zipFile = File(downloadPath, zipFileName)
                        
                        // Create ZIP archive
                        createZipFromDirectory(tempDir, zipFile)
                        
                        // Clean up temp directory
                        tempDir.deleteRecursively()
                        
                        val handler = Handler(requireContext().mainLooper)
                        handler.post {
                            Toast.makeText(requireContext(), R.string.toast_download_all_success, Toast.LENGTH_LONG).show()
                            binding.progressDl.visibility = GONE
                            binding.buttonDownloadAll.isEnabled = true
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        tempDir.deleteRecursively()
                        
                        val handler = Handler(requireContext().mainLooper)
                        handler.post {
                            Toast.makeText(requireContext(), R.string.toast_download_all_failed, Toast.LENGTH_LONG).show()
                            binding.progressDl.visibility = GONE
                            binding.buttonDownloadAll.isEnabled = true
                        }
                    }
                } else {
                    throwable?.printStackTrace()
                    tempDir.deleteRecursively()
                    
                    val handler = Handler(requireContext().mainLooper)
                    handler.post {
                        Toast.makeText(requireContext(), R.string.toast_download_all_failed, Toast.LENGTH_LONG).show()
                        binding.progressDl.visibility = GONE
                        binding.buttonDownloadAll.isEnabled = true
                    }
                }
            }
    }

    private fun createZipFromDirectory(sourceDir: File, zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
            sourceDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val relativePath = file.relativeTo(sourceDir).path
                    val entry = ZipEntry(relativePath)
                    zipOut.putNextEntry(entry)
                    
                    FileInputStream(file).use { input ->
                        input.copyTo(zipOut)
                    }
                    
                    zipOut.closeEntry()
                }
            }
        }
    }
}

class SnapshotFilesListAdapter(
    private val context: Context,
    private val files: ArrayList<ResticFile>,
    private val resticRepo: ResticRepo,
    private val snapshotId: ResticSnapshotId,
    private val rootPath: File,
    private val progressDl: ProgressBar
) : BaseAdapter() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val pathNameText: TextView = view.findViewById(R.id.pathname)
        val fileDateText: TextView = view.findViewById(R.id.filedate)
    }

    private var sortOrderDesc: Boolean? = null
    private var sortedFiles: ArrayList<ResticFile> = ArrayList(files)

    fun triggerSort(listFilesSnapshot: ListView) {
        sortOrderDesc = when (sortOrderDesc) {
            null -> {
                sortedFiles.sortByDescending { it.mtime }
                true
            }
            true -> {
                sortedFiles.sortBy { it.mtime }
                false
            }
            false -> {
                sortedFiles = ArrayList(files)
                null
            }
        }

        listFilesSnapshot.invalidateViews()
    }

    override fun getCount(): Int = sortedFiles.size

    override fun getItem(position: Int): Any = position

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View? {
        lateinit var holder: RecyclerView.ViewHolder
        val view =
            if (convertView == null) {
                val inflater = LayoutInflater.from(context)
                val view = inflater.inflate(R.layout.listitem_file, parent, false)
                holder = ViewHolder(view)
                view.tag = holder
                view
            } else {
                holder = convertView.tag as ViewHolder
                convertView
            }

        val file = sortedFiles[position]
        val relativePath = file.path.relativeTo(rootPath).toString()
        val fileName = file.path.name

        holder.pathNameText.text = fileName
        
        // Hide path if it's the same as filename (file at root level)
        if (relativePath == fileName) {
            holder.fileDateText.visibility = GONE
        } else {
            holder.fileDateText.visibility = VISIBLE
            holder.fileDateText.text = relativePath
        }

        // Add a click listener to initiate download
        holder.itemView.setOnClickListener {
            if (file.type == "file") {
                // Path where the downloaded file will be saved on the device
                val sharedPref = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                val downloadPathString = sharedPref?.getString("dl_path", "") ?: ""
                val downloadPath = File(downloadPathString)

                if (!(downloadPath.exists() && downloadPath.isDirectory)) {
                    AlertDialog.Builder(context)
                        .setTitle(R.string.alert_download_file_title)
                        .setMessage(R.string.alert_download_file_no_dest_dir)
                        .setNegativeButton(android.R.string.cancel) { _, _ -> }
                        .show()
                } else {
                    AlertDialog.Builder(context)
                        .setTitle(R.string.alert_download_file_title)
                        .setMessage(context.getString(R.string.alert_download_file_message, file.path.name, downloadPathString))
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            downloadFile(file, downloadPath)
                        }
                        .setNegativeButton(android.R.string.cancel) { _, _ -> }
                        .show()
                }
            }
        }

        return view
    }

    // Function to handle file download
    private fun downloadFile(file: ResticFile, downloadPath: File) {
        progressDl.visibility = VISIBLE
        resticRepo.restore(snapshotId, downloadPath, file)
            .handle { content, throwable ->
                val handler = Handler(context.mainLooper)
                handler.post {
                    if (content != null) {
                        // Notify the user that the download is complete
                        // You can use a Toast or other UI element to display this message
                        showToast("File downloaded")
                    } else {
                        throwable?.printStackTrace()

                        // Notify the user that an error occurred during download
                        showToast("Failed to download")
                    }
                    progressDl.visibility = GONE
                }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

}