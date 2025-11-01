package org.dydlakcloud.resticopia.ui.repos

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import org.dydlakcloud.resticopia.R
import org.dydlakcloud.resticopia.config.RepoConfig
import org.dydlakcloud.resticopia.config.RepoType

/**
 * Custom adapter for displaying repository list items with type-specific icons.
 */
class RepoListAdapter(
    private val context: Context,
    private val repos: List<RepoConfig>
) : BaseAdapter() {

    override fun getCount(): Int = repos.size

    override fun getItem(position: Int): RepoConfig = repos[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.list_item_repo, parent, false)

        val repo = getItem(position)
        
        val nameView = view.findViewById<TextView>(R.id.repo_name)
        val typeView = view.findViewById<TextView>(R.id.repo_type)

        // Set repository name
        nameView.text = repo.base.name

        // Set repository type text
        val typeText = when (repo.base.type) {
            RepoType.Local -> "Local"
            RepoType.S3 -> "S3"
            RepoType.B2 -> "B2"
            RepoType.Rest -> "REST Server"
            RepoType.Rclone -> "Rclone"
        }
        typeView.text = typeText

        return view
    }
}

