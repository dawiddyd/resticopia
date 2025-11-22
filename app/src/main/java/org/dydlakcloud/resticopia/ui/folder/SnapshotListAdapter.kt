package org.dydlakcloud.resticopia.ui.folder

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import org.dydlakcloud.resticopia.R
import org.dydlakcloud.resticopia.restic.ResticSnapshot
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Custom adapter for displaying snapshot list items
 */
class SnapshotListAdapter(
    private val context: Context,
    private val snapshots: List<ResticSnapshot>,
    private val repoName: String
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

        // Set snapshot hash (short version)
        hashView.text = snapshot.id.short

        // Set date and repo name
        val formattedDate = snapshot.time.format(dateFormatter)
        detailsView.text = "$formattedDate $repoName"

        return view
    }
}

