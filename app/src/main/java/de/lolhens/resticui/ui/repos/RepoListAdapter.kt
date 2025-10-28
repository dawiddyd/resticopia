package de.lolhens.resticui.ui.repos

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import de.lolhens.resticui.R
import de.lolhens.resticui.config.RepoConfig
import de.lolhens.resticui.config.RepoType

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
        
        val iconView = view.findViewById<ImageView>(R.id.repo_icon)
        val nameView = view.findViewById<TextView>(R.id.repo_name)

        // Set repository name
        nameView.text = repo.base.name

        // Set icon based on repository type
        val iconResource = when (repo.base.type) {
            RepoType.Local -> R.drawable.ic_repo_local
            RepoType.S3 -> R.drawable.ic_repo_s3
            RepoType.B2 -> R.drawable.ic_repo_b2
            RepoType.Rest -> R.drawable.ic_repo_rest
        }
        iconView.setImageResource(iconResource)

        return view
    }
}

