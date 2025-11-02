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

/**
 * Editor Activity for GitIgnore-style patterns.
 * 
 * Provides a full-screen text editor for users to define file/folder exclusion patterns
 * similar to .gitignore syntax. Follows the same UX pattern as RcloneConfigEditorActivity.
 * 
 * Supported pattern syntax:
 * - # for comments
 * - *.ext for wildcard matching
 * - folder/ for directory matching
 * - /path for root-anchored patterns
 * - !pattern for negation
 */
class IgnorePatternsEditorActivity : AppCompatActivity() {
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
            val currentPatterns = intent.getStringExtra("patterns") ?: ""
            setText(currentPatterns)
            hint = getString(R.string.ignore_patterns_editor_hint)
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
            title = getString(R.string.ignore_patterns_editor_title)
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_ignore_patterns_editor, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // Back button pressed - show discard confirmation if changed
                val currentContent = intent.getStringExtra("patterns") ?: ""
                val newContent = editor.text.toString()
                
                if (currentContent != newContent) {
                    showDiscardChangesDialog()
                } else {
                    finish()
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                }
                true
            }
            R.id.action_save -> {
                saveAndFinish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onBackPressed() {
        // Handle back button same as home button
        val currentContent = intent.getStringExtra("patterns") ?: ""
        val newContent = editor.text.toString()
        
        if (currentContent != newContent) {
            showDiscardChangesDialog()
        } else {
            super.onBackPressed()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }
    
    private fun showDiscardChangesDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.ignore_patterns_discard_title)
            .setMessage(R.string.ignore_patterns_discard_message)
            .setPositiveButton(R.string.action_discard) { _, _ -> 
                finish()
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun saveAndFinish() {
        val content = editor.text.toString().trim()
        
        val resultIntent = Intent().apply {
            putExtra("patterns", content)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}

