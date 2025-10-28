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

/**
 * Custom adapter for displaying folder list items with consistent sizing.
 */
class FolderListAdapter(
    private val context: Context,
    private val folders: List<FolderConfig>,
    private val config: Config
) : BaseAdapter() {

    override fun getCount(): Int = folders.size

    override fun getItem(position: Int): FolderConfig = folders[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.list_item_folder, parent, false)

        val folder = getItem(position)
        
        val nameView = view.findViewById<TextView>(R.id.folder_name)

        // Set folder name with repository info
        val repoName = folder.repo(config)?.base?.name ?: "Unknown"
        nameView.text = "$repoName ${folder.path.path}"

        return view
    }
}

