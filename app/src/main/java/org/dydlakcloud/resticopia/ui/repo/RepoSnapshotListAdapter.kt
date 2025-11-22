package org.dydlakcloud.resticopia.ui.repo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import org.dydlakcloud.resticopia.R
import org.dydlakcloud.resticopia.restic.ResticSnapshot
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Custom adapter for displaying repository snapshot list items
 */
class RepoSnapshotListAdapter(
    private val context: Context,
    private val snapshots: List<ResticSnapshot>
) : BaseAdapter() {

    private val dateFormatter = DateTimeFormatter.ofPattern("HH:mm MMM dd, yyyy").withZone(ZoneId.systemDefault())

    override fun getCount(): Int = snapshots.size

    override fun getItem(position: Int): ResticSnapshot = snapshots[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.list_item_snapshot, parent, false)

        val snapshot = getItem(position)
        
        val hashView = view.findViewById<TextView>(R.id.snapshot_hash)
        val detailsView = view.findViewById<TextView>(R.id.snapshot_details)

        // Get folder name from the path (just the directory name, not full path)
        val folderName = if (snapshot.paths.isNotEmpty()) {
            File(snapshot.paths[0].path).name
        } else {
            "Unknown"
        }
        hashView.text = folderName

        // Set date and snapshot hash
        val formattedDate = snapshot.time.format(dateFormatter)
        detailsView.text = "$formattedDate ${snapshot.id.short}"

        return view
    }
}

