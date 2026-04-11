package com.screentextcopier

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.screentextcopier.model.ClipboardItem
import java.io.File

/**
 * Manages persistent clipboard history using SharedPreferences (JSON-serialized).
 * Items are stored in order of most-recent-first.
 */
class ClipboardHistoryManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listeners = mutableListOf<() -> Unit>()

    private var items: MutableList<ClipboardItem> = loadItems()

    /** Returns an immutable snapshot of the current clipboard history. */
    fun getItems(): List<ClipboardItem> = items.toList()

    /** Adds a new item to the top of the clipboard history and persists it. */
    fun addItem(item: ClipboardItem) {
        items.add(0, item)
        if (items.size > MAX_ITEMS) {
            val removed = items.removeAt(items.lastIndex)
            removed.imagePath?.let { File(it).delete() }
        }
        saveItems()
        notifyListeners()
    }

    /** Removes an item by its id. */
    fun removeItem(id: String) {
        val removed = items.find { it.id == id }
        items.removeAll { it.id == id }
        removed?.imagePath?.let { File(it).delete() }
        saveItems()
        notifyListeners()
    }

    /** Clears all clipboard history. */
    fun clearAll() {
        items.forEach { it.imagePath?.let { path -> File(path).delete() } }
        items.clear()
        saveItems()
        notifyListeners()
    }

    /** Register a listener that is called whenever the history changes. */
    fun addChangeListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeChangeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        listeners.forEach { it() }
    }

    private fun loadItems(): MutableList<ClipboardItem> {
        val json = prefs.getString(KEY_ITEMS, null) ?: return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<ClipboardItem>>() {}.type
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun saveItems() {
        prefs.edit().putString(KEY_ITEMS, gson.toJson(items)).apply()
    }

    companion object {
        private const val PREFS_NAME = "clipboard_history"
        private const val KEY_ITEMS = "items"
        private const val MAX_ITEMS = 100
    }
}
