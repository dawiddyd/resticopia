package de.lolhens.resticui.ui.repo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import de.lolhens.resticui.R
import de.lolhens.resticui.config.RepoType

/**
 * Custom spinner adapter for displaying repository types with their corresponding icons.
 */
class RepoTypeSpinnerAdapter(
    context: Context,
    private val repoTypes: Array<RepoType>
) : ArrayAdapter<RepoType>(context, R.layout.spinner_repo_type_item, repoTypes) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createView(position, convertView, parent, R.layout.spinner_repo_type_item)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createView(position, convertView, parent, R.layout.spinner_repo_type_dropdown_item)
    }

    private fun createView(position: Int, convertView: View?, parent: ViewGroup, layoutResource: Int): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(layoutResource, parent, false)

        val repoType = getItem(position)!!
        
        val iconView = view.findViewById<ImageView>(R.id.repo_type_icon)
        val nameView = view.findViewById<TextView>(R.id.repo_type_name)

        // Set repository type name
        nameView.text = repoType.name

        // Set icon based on repository type
        val iconResource = when (repoType) {
            RepoType.Local -> R.drawable.ic_repo_local
            RepoType.S3 -> R.drawable.ic_repo_s3
            RepoType.B2 -> R.drawable.ic_repo_b2
            RepoType.Rest -> R.drawable.ic_repo_rest
        }
        iconView.setImageResource(iconResource)

        return view
    }
}

