package org.dydlakcloud.resticopia.ui.folders

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import org.dydlakcloud.resticopia.R
import org.dydlakcloud.resticopia.config.Config
import org.dydlakcloud.resticopia.config.FolderConfig
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Custom adapter for displaying folder list items with consistent sizing.
 */
class FolderListAdapter(
    private val context: Context,
    private val folders: List<FolderConfig>,
    private val config: Config
) : BaseAdapter() {

    private val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy").withZone(ZoneId.systemDefault())

    override fun getCount(): Int = folders.size

    override fun getItem(position: Int): FolderConfig = folders[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.list_item_folder, parent, false)

        val folder = getItem(position)
        
        val nameView = view.findViewById<TextView>(R.id.folder_name)
        val detailsView = view.findViewById<TextView>(R.id.folder_details)

        // Set folder name (just the name, not full path)
        nameView.text = folder.path.name

        // Build details line: "Repository name, last backup Mar 25, 2023"
        val repoName = folder.repo(config)?.base?.name ?: "Unknown"
        val lastBackup = folder.lastBackup(filterSuccessful = true)
        
        val detailsText = if (lastBackup != null) {
            val formattedDate = lastBackup.timestamp.withZoneSameInstant(java.time.ZoneId.systemDefault()).format(dateFormatter)
            "$repoName, last backup $formattedDate"
        } else {
            "$repoName, no backups yet"
        }
        
        detailsView.text = detailsText

        return view
    }
}

