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
    private var originalItems: List<ClipboardItem> = items.toList()
    private var filterPinned: Boolean = false
    private var filterType: String? = null // e.g. "url", "email", "phone", null = all
    private var sortByDateDesc: Boolean = true

    fun setFilterPinned(enabled: Boolean) {
        filterPinned = enabled
        filterAndSearch()
    }
    fun setFilterType(type: String?) {
        filterType = type
        filterAndSearch()
    }
    fun setSortByDate(desc: Boolean) {
        sortByDateDesc = desc
        filterAndSearch()
    }
    fun search(query: String?) {
        filterAndSearch(query)
    }
    private fun filterAndSearch(query: String? = null) {
        var filtered = originalItems
        if (filterPinned) filtered = filtered.filter { it.isPinned }
        if (!filterType.isNullOrEmpty()) {
            filtered = filtered.filter { matchesType(it, filterType) }
        }
        if (!query.isNullOrEmpty()) {
            val q = query.lowercase()
            filtered = filtered.filter { it.text.lowercase().contains(q) }
        }
        filtered = if (sortByDateDesc) filtered.sortedByDescending { it.timestamp } else filtered.sortedBy { it.timestamp }
        items = filtered.toMutableList()
        notifyDataSetChanged()
    }
    private fun matchesType(item: ClipboardItem, type: String?): Boolean {
        return when(type) {
            "url" -> item.text.startsWith("http://") || item.text.startsWith("https://")
            "email" -> android.util.Patterns.EMAIL_ADDRESS.matcher(item.text).matches()
            "phone" -> android.util.Patterns.PHONE.matcher(item.text).matches()
            else -> true
        }
    }

    inner class ClipboardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: CardView = itemView as CardView
        val border: View = itemView.findViewById(R.id.item_border)
        val text: TextView = itemView.findViewById(R.id.item_text)
        val time: TextView = itemView.findViewById(R.id.item_time)
        val btnPin: ImageButton = itemView.findViewById(R.id.btn_pin)
        val btnAction: ImageButton = itemView.findViewById(R.id.btn_action)
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
            holder.card.setCardBackgroundColor(context.getColorFromAttr(com.google.android.material.R.attr.colorSecondaryContainer))
            holder.border.setBackgroundColor(context.getColorFromAttr(com.google.android.material.R.attr.colorPrimary))
            holder.btnPin.setImageResource(android.R.drawable.btn_star_big_on)
            holder.btnPin.setColorFilter(context.getColorFromAttr(com.google.android.material.R.attr.colorPrimary))
        } else {
            holder.card.setCardBackgroundColor(context.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceVariant))
            holder.border.setBackgroundColor(context.getColorFromAttr(com.google.android.material.R.attr.colorPrimary))
            holder.btnPin.setImageResource(android.R.drawable.btn_star_big_off)
            holder.btnPin.setColorFilter(context.getColorFromAttr(com.google.android.material.R.attr.colorOutline))
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
        // --- Smart content recognition and quick actions ---
        val text = item.text.trim()
        val (actionType, iconRes, tintColor, contentDesc) = when {
            android.util.Patterns.WEB_URL.matcher(text).matches() && (text.startsWith("http://") || text.startsWith("https://")) ->
                arrayOf("url", android.R.drawable.ic_menu_view, context.getColorFromAttr(com.google.android.material.R.attr.colorPrimary), "Open link")
            android.util.Patterns.EMAIL_ADDRESS.matcher(text).matches() ->
                arrayOf("email", android.R.drawable.ic_dialog_email, context.getColorFromAttr(com.google.android.material.R.attr.colorTertiary), "Send email")
            android.util.Patterns.PHONE.matcher(text).matches() ->
                arrayOf("phone", android.R.drawable.ic_menu_call, context.getColorFromAttr(com.google.android.material.R.attr.colorSecondary), "Call number")
            else ->
                arrayOf(null, android.R.drawable.ic_menu_share, context.getColorFromAttr(com.google.android.material.R.attr.colorOutline), "No quick action")
        }
        if (actionType != null) {
            holder.btnAction.visibility = View.VISIBLE
            holder.btnAction.setImageResource(iconRes as Int)
            holder.btnAction.setColorFilter(tintColor as Int)
            holder.btnAction.contentDescription = contentDesc as String
            holder.btnAction.setOnClickListener {
                when (actionType) {
                    "url" -> try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(text))
                        context.startActivity(intent)
                    } catch (e: Exception) { Toast.makeText(context, "No app to open link", Toast.LENGTH_SHORT).show() }
                    "email" -> try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:" + text))
                        context.startActivity(intent)
                    } catch (e: Exception) { Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show() }
                    "phone" -> try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + text))
                        context.startActivity(intent)
                    } catch (e: Exception) { Toast.makeText(context, "No dialer app found", Toast.LENGTH_SHORT).show() }
                }
            }
        } else {
            holder.btnAction.visibility = View.INVISIBLE
            holder.btnAction.setOnClickListener(null)
        }
        // --- End smart content recognition ---
        // Swipe to delete is handled by ItemTouchHelper in OverlayManager
    }

    fun updateData(newItems: List<ClipboardItem>) {
        items = newItems.toMutableList()
        notifyDataSetChanged()
    }

    private fun animatePin(view: View) {
        // Bounce scale up, rotate, then scale back
        view.animate().scaleX(1.2f).scaleY(1.2f).setDuration(90).withEndAction {
            view.animate().rotationBy(360f).setDuration(180).withEndAction {
                view.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
                view.rotation = 0f
            }.start()
        }.start()
        // Animate color filter if ImageButton
        if (view is ImageButton) {
            val fromColor = context.getColorFromAttr(com.google.android.material.R.attr.colorOutline)
            val toColor = if (view.drawable.constantState == context.getDrawable(android.R.drawable.btn_star_big_on)?.constantState)
                context.getColorFromAttr(com.google.android.material.R.attr.colorPrimary)
            else
                context.getColorFromAttr(com.google.android.material.R.attr.colorOutline)
            val colorAnim = android.animation.ValueAnimator.ofArgb(fromColor, toColor)
            colorAnim.duration = 220
            colorAnim.addUpdateListener { animator ->
                view.setColorFilter(animator.animatedValue as Int)
            }
            colorAnim.start()
        }
    }

    fun removeItem(position: Int) {
        items.removeAt(position)
        notifyItemRemoved(position)
    }

    fun getItemAt(position: Int): ClipboardItem? = items.getOrNull(position)
}
