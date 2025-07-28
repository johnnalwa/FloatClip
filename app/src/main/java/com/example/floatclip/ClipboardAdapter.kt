package com.example.floatclip

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import androidx.cardview.widget.CardView

class ClipboardAdapter(
    private val context: Context,
    private var items: MutableList<ClipboardItem>,
    private val onPinToggle: (ClipboardItem) -> Unit,
    private val onDelete: (ClipboardItem) -> Unit,
    private val onCopy: (ClipboardItem) -> Unit
) : RecyclerView.Adapter<ClipboardAdapter.ClipboardViewHolder>() {

    inner class ClipboardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: CardView = itemView as CardView
        val border: View = itemView.findViewById(R.id.item_border)
        val text: TextView = itemView.findViewById(R.id.item_text)
        val time: TextView = itemView.findViewById(R.id.item_time)
        val btnPin: ImageButton = itemView.findViewById(R.id.btn_pin)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClipboardViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_clipboard, parent, false)
        return ClipboardViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ClipboardViewHolder, position: Int) {
        val item = items[position]
        holder.text.text = item.text
        holder.time.text = android.text.format.DateFormat.format("MMM d, h:mm a", item.timestamp)
        // Style pinned
        if (item.isPinned) {
            holder.card.setCardBackgroundColor(Color.parseColor("#fef9e7"))
            holder.border.setBackgroundColor(Color.parseColor("#f39c12"))
            holder.btnPin.setImageResource(android.R.drawable.btn_star_big_on)
            holder.btnPin.setColorFilter(Color.parseColor("#f39c12"))
        } else {
            holder.card.setCardBackgroundColor(Color.parseColor("#f8f9fa"))
            holder.border.setBackgroundColor(Color.parseColor("#3498db"))
            holder.btnPin.setImageResource(android.R.drawable.btn_star_big_off)
            holder.btnPin.setColorFilter(Color.parseColor("#bdbdbd"))
        }
        holder.btnPin.setOnClickListener {
            onPinToggle(item)
            animatePin(holder.btnPin)
        }
        holder.card.setOnClickListener {
            onCopy(item)
            Toast.makeText(context, "Pasted!", Toast.LENGTH_SHORT).show()
        }
        holder.card.setOnLongClickListener {
            // Optionally show more actions
            false
        }
        // Swipe to delete is handled by ItemTouchHelper in OverlayManager
    }

    fun updateData(newItems: List<ClipboardItem>) {
        items = newItems.toMutableList()
        notifyDataSetChanged()
    }

    private fun animatePin(view: View) {
        view.animate().rotationBy(360f).setDuration(200).start()
        Handler(Looper.getMainLooper()).postDelayed({
            view.rotation = 0f
        }, 220)
    }

    fun removeItem(position: Int) {
        items.removeAt(position)
        notifyItemRemoved(position)
    }

    fun getItemAt(position: Int): ClipboardItem? = items.getOrNull(position)
}
