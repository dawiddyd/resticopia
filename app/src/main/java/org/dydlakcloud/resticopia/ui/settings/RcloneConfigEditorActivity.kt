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
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import org.dydlakcloud.resticopia.R
import org.dydlakcloud.resticopia.util.RcloneConfigParser

class RcloneConfigEditorActivity : AppCompatActivity() {
    private lateinit var editor: EditText
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create toolbar with theme attributes for proper light/dark mode support
        val toolbar = Toolbar(this).apply {
            // Get background color from theme
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
            setBackgroundColor(typedValue.data)
            elevation = 0f
        }
        
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
        
        // Create root container with vertical layout
        val rootContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            
            // Get background color from theme for root container
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)
            setBackgroundColor(typedValue.data)
            
            // Add toolbar
            addView(toolbar, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            
            // Add editor
            addView(editor, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ))
        }
        
        setContentView(rootContainer)
        
        // Setup toolbar as action bar
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
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
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
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
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}

