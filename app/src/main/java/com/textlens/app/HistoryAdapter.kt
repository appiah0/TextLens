package com.textlens.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(
    private val items: List<String>,
    private val onCopy: (String) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val tvPreview: TextView = view.findViewById(R.id.tvPreview)
        val btnCopy: View = view.findViewById(R.id.btnCopyHistory)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val timestamp = item.substringBefore("|")
        val text = item.substringAfter("|")

        // Format timestamp: "yyyyMMddHHmmss" → readable
        val date = try {
            val sdf = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
            val parsed = sdf.parse(timestamp)
            SimpleDateFormat("MMM d, h:mm a", Locale.US).format(parsed ?: Date())
        } catch (e: Exception) {
            "Unknown date"
        }

        holder.tvDate.text = date
        holder.tvPreview.text = text.take(120)
        holder.btnCopy.setOnClickListener { onCopy(item) }
    }

    override fun getItemCount() = items.size
}
