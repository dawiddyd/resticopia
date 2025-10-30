package org.dydlakcloud.resticopia.ui.settings

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.dydlakcloud.resticopia.R
import org.dydlakcloud.resticopia.util.RcloneConfigParser

class RcloneConfigEditorActivity : AppCompatActivity() {
    private lateinit var editor: EditText
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create full-screen editor
        editor = EditText(this).apply {
            val currentConfig = intent.getStringExtra("config") ?: ""
            setText(currentConfig)
            hint = getString(R.string.rclone_editor_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            typeface = Typeface.MONOSPACE
            setHorizontallyScrolling(true)
            setPadding(32, 32, 32, 32)
            textSize = 14f
            gravity = Gravity.TOP or Gravity.START
        }
        
        // Wrap in FrameLayout to ensure top alignment
        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(editor, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            })
        }
        
        setContentView(container)
        
        // Setup action bar
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.rclone_editor_title)
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_rclone_editor, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // Back button pressed
                finish()
                true
            }
            R.id.action_save -> {
                val content = editor.text.toString().trim()
                
                // Validate configuration
                if (content.isEmpty()) {
                    // Allow empty config (disables rclone repos)
                    saveAndFinish(content)
                } else {
                    val remotes = try {
                        RcloneConfigParser.parseConfigContent(content)
                    } catch (e: Exception) {
                        emptyList()
                    }
                    
                    if (remotes.isEmpty()) {
                        // Show warning for invalid config
                        AlertDialog.Builder(this)
                            .setTitle(R.string.rclone_config_invalid_title)
                            .setMessage(R.string.rclone_config_invalid_message)
                            .setPositiveButton(R.string.action_save) { _, _ -> 
                                saveAndFinish(content) 
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                    } else {
                        // Valid config with remotes
                        saveAndFinish(content)
                    }
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun saveAndFinish(content: String) {
        val result = Intent().putExtra("config", content)
        setResult(RESULT_OK, result)
        finish()
    }
}

