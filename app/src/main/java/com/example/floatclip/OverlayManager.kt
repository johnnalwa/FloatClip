package com.example.floatclip

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar

class OverlayManager(private val context: Context) {
    private var recentlyDeletedItem: ClipboardItem? = null
    private var recentlyDeletedPosition: Int = -1
    private var windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var expandedView: View? = null
    private var isAdded = false
    private var isExpanded = false
    private var params: WindowManager.LayoutParams? = null
    private val dbHelper = ClipboardDatabaseHelper(context)
    private var clipboardAdapter: ClipboardAdapter? = null
    private val handler = Handler(Looper.getMainLooper())

    private fun showUndoSnackbar(parent: View, recycler: RecyclerView) {
        recentlyDeletedItem?.let { item ->
            Snackbar.make(parent, "Item deleted", Snackbar.LENGTH_LONG)
                .setAction("Undo") {
                    dbHelper.insertClipboardItem(item.text, item.timestamp, item.isPinned, item.hash)
                    reloadClipboardItems()
                    recycler.scrollToPosition(0)
                }
                .show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun showCollapsedWidget() {
        if (isAdded) return
        // Read widget size and position memory from settings
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val sizePref = prefs.getString("widget_size", "medium")
        val sizeDp = when(sizePref) {
            "small" -> 44
            "large" -> 80
            else -> 60
        }
        val marginDp = 15
        val sizePx = dpToPx(sizeDp)
        val marginPx = dpToPx(marginDp)
        val rememberPosition = prefs.getBoolean("position_memory", true)
        val lastSavedX = prefs.getInt("widget_x", marginPx)
        val lastSavedY = prefs.getInt("widget_y", marginPx)
        val inflater = LayoutInflater.from(context)
        val widget = FrameLayout(context)
        val icon = ImageView(context)
        icon.setImageResource(R.drawable.ic_clipboard)
        icon.setColorFilter(context.getColorFromAttr(com.google.android.material.R.attr.colorPrimary), PorterDuff.Mode.SRC_IN)
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(context.getColorFromAttr(com.google.android.material.R.attr.colorSecondaryContainer))
        bg.setStroke(dpToPx(1), context.getColorFromAttr(com.google.android.material.R.attr.colorPrimary))
        widget.background = bg
        widget.elevation = dpToPx(6).toFloat()
        widget.addView(icon, FrameLayout.LayoutParams(sizePx, sizePx, Gravity.CENTER))
        val lp = FrameLayout.LayoutParams(sizePx, sizePx)
        widget.layoutParams = lp
        widget.setPadding(0,0,0,0)

        // Touch/drag logic
        var touchStartX = 0f
        var touchStartY = 0f
        var dX = 0f
        var dY = 0f
        widget.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    dX = v.x - event.rawX
                    dY = v.y - event.rawY
                    animateScale(v, 0.9f)
                }
                MotionEvent.ACTION_MOVE -> {
                    val newX = event.rawX + dX
                    val newY = event.rawY + dY
                    v.x = newX
                    v.y = newY
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    animateScale(v, 1.0f)
                    // Snap to edge (simple)
                    val screenWidth = context.resources.displayMetrics.widthPixels
                    v.x = if (v.x + sizePx/2 > screenWidth/2) (screenWidth-sizePx-marginPx).toFloat() else marginPx.toFloat()
                    v.y = v.y.coerceIn(marginPx.toFloat(), (context.resources.displayMetrics.heightPixels-sizePx-marginPx).toFloat())
                    // Save position if enabled
                    if (rememberPosition) {
                        prefs.edit().putInt("widget_x", v.x.toInt()).putInt("widget_y", v.y.toInt()).apply()
                    }
                }
            }
            false
        }
        // Set initial position
        widget.x = if (rememberPosition) lastSavedX.toFloat() else marginPx.toFloat()
        widget.y = if (rememberPosition) lastSavedY.toFloat() else marginPx.toFloat()
        widget.setOnClickListener {
            animateScale(widget, 1.0f)
            showExpandedPanel()
        }
        widget.setOnLongClickListener {
            // TODO: Show settings
            true
        }

        val layoutParams = WindowManager.LayoutParams(
            sizePx, sizePx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        layoutParams.gravity = Gravity.TOP or Gravity.END
        layoutParams.x = marginPx
        layoutParams.y = marginPx
        params = layoutParams
        overlayView = widget
        windowManager.addView(widget, layoutParams)
        isAdded = true
    }

    fun removeWidget() {
        if (isAdded && overlayView != null) {
            windowManager.removeView(overlayView)
            overlayView = null
            isAdded = false
        }
        if (isExpanded && expandedView != null) {
            windowManager.removeView(expandedView)
            expandedView = null
            isExpanded = false
        }
    }

    // EXPANDED PANEL LOGIC
    fun showExpandedPanel() {
        if (isExpanded) return
        hideCollapsedWidget()
        val inflater = LayoutInflater.from(context)
        val expanded = inflater.inflate(R.layout.overlay_expanded_panel, null)
        // Blur effect for Android 12+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            expanded.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            expanded.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(24f, 24f, android.graphics.Shader.TileMode.CLAMP))
        } else {
            expanded.setBackgroundColor(0xF2FFFFFF.toInt()) // fallback: 95% white
        }
        expanded.alpha = 0f
        expanded.scaleX = 0.95f
        expanded.scaleY = 0.95f
        val recycler = expanded.findViewById<RecyclerView>(R.id.recycler_clipboard)
        val btnClose = expanded.findViewById<View>(R.id.btn_close)
        val btnSettings = expanded.findViewById<View>(R.id.btn_settings)
        btnSettings.setOnClickListener {
            try {
                val intent = android.content.Intent(context, SettingsActivity::class.java)
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                hideExpandedPanel()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Unable to open settings", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        val searchBar = expanded.findViewById<android.widget.EditText>(R.id.search_bar)
        val filterPinned = expanded.findViewById<android.widget.ImageButton>(R.id.filter_pinned)
        val filterDate = expanded.findViewById<android.widget.ImageButton>(R.id.filter_date)
        val filterType = expanded.findViewById<android.widget.ImageButton>(R.id.filter_type)
        clipboardAdapter = ClipboardAdapter(
            context,
            dbHelper.getAllItems().toMutableList(),
            onPinToggle = { item ->
                dbHelper.updatePinStatus(item.id, !item.isPinned)
                reloadClipboardItems()
            },
            onDelete = { item ->
                dbHelper.deleteItem(item.id)
                reloadClipboardItems()
            },
            onCopy = { item ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Copied", item.text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Pasted!", Toast.LENGTH_SHORT).show()
            }
        )
        recycler.adapter = clipboardAdapter
        recycler.layoutManager = LinearLayoutManager(context)

        // Swipe to delete
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {
                val pos = vh.adapterPosition
                val item = clipboardAdapter?.getItemAt(pos)
                if (item != null) {
                    recentlyDeletedItem = item.copy()
                    recentlyDeletedPosition = pos
                    dbHelper.deleteItem(item.id)
                    reloadClipboardItems()
                    showUndoSnackbar(expanded, recycler)
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(recycler)

        // --- Search & Filter UI logic ---
        searchBar.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                clipboardAdapter?.search(s?.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        var pinnedFilterOn = false
        filterPinned.setOnClickListener {
            pinnedFilterOn = !pinnedFilterOn
            clipboardAdapter?.setFilterPinned(pinnedFilterOn)
            filterPinned.alpha = if (pinnedFilterOn) 1.0f else 0.4f
        }
        var dateDesc = true
        filterDate.setOnClickListener {
            dateDesc = !dateDesc
            clipboardAdapter?.setSortByDate(dateDesc)
            filterDate.rotation = if (dateDesc) 0f else 180f
        }
        var typeFilterIdx = 0
        val typeOrder = arrayOf(null, "url", "email", "phone")
        filterType.setOnClickListener {
            typeFilterIdx = (typeFilterIdx + 1) % typeOrder.size
            clipboardAdapter?.setFilterType(typeOrder[typeFilterIdx])
            // Optionally update icon tint or contentDesc for feedback
            filterType.alpha = if (typeOrder[typeFilterIdx] != null) 1.0f else 0.4f
        }
        // --- End Search & Filter UI logic ---

        // Close button
        btnClose.setOnClickListener { hideExpandedPanel() }

        // Animate expand
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            dpToPx(400),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        layoutParams.gravity = Gravity.TOP
        layoutParams.x = dpToPx(15)
        layoutParams.y = dpToPx(15)
        expandedView = expanded
        windowManager.addView(expanded, layoutParams)
        isExpanded = true
        expanded.animate().alpha(1f).setDuration(300).start()
    }

    fun hideExpandedPanel() {
        if (isExpanded && expandedView != null) {
            expandedView?.animate()?.alpha(0f)?.scaleX(0.95f)?.scaleY(0.95f)?.setDuration(220)?.withEndAction {
                windowManager.removeView(expandedView)
                expandedView = null
                isExpanded = false
                showCollapsedWidget()
            }?.start()
        }
    }

    private fun hideCollapsedWidget() {
        if (isAdded && overlayView != null) {
            windowManager.removeView(overlayView)
            overlayView = null
            isAdded = false
        }
    }

    private fun reloadClipboardItems() {
        clipboardAdapter?.updateData(dbHelper.getAllItems())
    }

    private fun animateScale(view: View, scale: Float) {
        ValueAnimator.ofFloat(view.scaleX, scale).apply {
            duration = 150
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val value = it.animatedValue as Float
                view.scaleX = value
                view.scaleY = value
            }
            start()
        }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics).toInt()
    }
}
