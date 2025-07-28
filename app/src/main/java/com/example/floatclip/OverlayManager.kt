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
            com.google.android.material.snackbar.Snackbar.make(parent, "Item deleted", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                .setAction("Undo") {
                    dbHelper.insertClipboardItem(item.text, item.timestamp, item.isPinned, item.hash)
                    reloadClipboardItems()
                    recycler.scrollToPosition(0)
                }
                .show()
        }
    }

    // ... existing code ...

    @SuppressLint("ClickableViewAccessibility")
    fun showCollapsedWidget() {
        if (isAdded) return
        val sizeDp = 60
        val marginDp = 15
        val sizePx = dpToPx(sizeDp)
        val marginPx = dpToPx(marginDp)
        val inflater = LayoutInflater.from(context)
        val widget = FrameLayout(context)
        val icon = ImageView(context)
        icon.setImageResource(R.drawable.ic_clipboard)
        icon.setColorFilter(ContextCompat.getColor(context, android.R.color.transparent), PorterDuff.Mode.SRC_IN)
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(0xF2FFFFFF.toInt()) // 95% white
        bg.setStroke(dpToPx(1), 0x22000000)
        widget.background = bg
        widget.elevation = dpToPx(6).toFloat()
        widget.addView(icon, FrameLayout.LayoutParams(sizePx, sizePx, Gravity.CENTER))
        val lp = FrameLayout.LayoutParams(sizePx, sizePx)
        widget.layoutParams = lp
        widget.setPadding(0,0,0,0)

        // Touch/drag logic
        var lastX = 0f
        var lastY = 0f
        var dX = 0f
        var dY = 0f
        widget.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX
                    lastY = event.rawY
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
                }
            }
            false
        }
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
        val recycler = expanded.findViewById<RecyclerView>(R.id.recycler_clipboard)
        val btnClose = expanded.findViewById<View>(R.id.btn_close)
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
        var recentlyDeletedItem: ClipboardItem? = null
        var recentlyDeletedPosition: Int = -1
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
                    this@OverlayManager.showUndoSnackbar(expanded, recycler)
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(recycler)

    fun showUndoSnackbar(parent: View, recycler: RecyclerView) {
        recentlyDeletedItem?.let { item ->
            com.google.android.material.snackbar.Snackbar.make(parent, "Item deleted", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                .setAction("Undo") {
                    dbHelper.insertClipboardItem(item.text, item.timestamp, item.isPinned, item.hash)
                    reloadClipboardItems()
                    recycler.scrollToPosition(0)
                }
                .show()
        }
    }

        // Close button
        btnClose.setOnClickListener { hideExpandedPanel() }
        // Animate expand
        expanded.alpha = 0f
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
            expandedView?.animate()?.alpha(0f)?.setDuration(300)?.withEndAction {
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
