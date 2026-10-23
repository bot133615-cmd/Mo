package com.alphaxk.keymapperpro

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku
import kotlin.math.abs

class MainActivity : AppCompatActivity() {
    private var touchService: ITouchService? = null
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            touchService = ITouchService.Stub.asInterface(service)
            KeyMapperService.touchService = touchService
            Toast.makeText(this@MainActivity, "المحرك متصل وجاهز!", Toast.LENGTH_SHORT).show()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            touchService = null
            KeyMapperService.touchService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 60, 60, 60)
            setBackgroundColor(Color.parseColor("#0b0f14"))
        }

        layout.addView(TextView(this).apply {
            text = "ALPHA Keymapper Pro"
            setTextColor(Color.WHITE)
            textSize = 24f
            setPadding(0, 0, 0, 40)
        })

        layout.addView(Button(this).apply {
            text = "1. صلاحية الظهور (Overlay)"
            setOnClickListener { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) }
        })

        layout.addView(Button(this).apply {
            text = "2. تفعيل خدمة Accessibility"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })

        layout.addView(Button(this).apply {
            text = "3. تشغيل Shizuku وربط المحرك"
            setOnClickListener {
                if (checkShizukuPermission()) {
                    bindUserService()
                    startService(Intent(this@MainActivity, KeyMapperService::class.java))
                }
            }
        })

        setContentView(layout)

        Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == 1001 && grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                bindUserService()
            }
        }
    }

    private fun checkShizukuPermission(): Boolean {
        if (Shizuku.isPreV11() || Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) return true
        Shizuku.requestPermission(1001)
        return false
    }

    private fun bindUserService() {
        if (touchService != null) return
        val args = Shizuku.UserServiceArgs(ComponentName(packageName, TouchService::class.java.name))
            .daemon(false).processNameSuffix("touch_service").debuggable(false).version(1)
        Shizuku.bindUserService(args, connection)
    }
}

class KeyMapperService : AccessibilityService() {
    private lateinit var windowManager: WindowManager
    private var floatMenu: View? = null
    private var editPanel: LinearLayout? = null
    private var mouseArea: View? = null
    private var dPadView: View? = null
    
    private val buttons = mutableListOf<KeyButton>()
    private val activePointers = mutableMapOf<Int, MotionEvent.PointerCoords>()
    private var isEditMode = true
    private var isPanelVisible = false

    companion object {
        var touchService: ITouchService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = serviceInfo
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        serviceInfo = info
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        showFloatMenu()
    }

    private fun showFloatMenu() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            x = 20
        }

        val menuBtn = TextView(this).apply {
            text = "⚙"
            textSize = 30f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#AA000000"))
            setPadding(25, 25, 25, 25)
            setOnClickListener { toggleEditPanel() }
        }
        
        menuBtn.setOnTouchListener(object : View.OnTouchListener {
            var initialX = 0; var initialY = 0; var initialTouchX = 0f; var initialTouchY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x; initialY = params.y
                        initialTouchX = event.rawX; initialTouchY = event.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatMenu, params)
                    }
                }
                return false
            }
        })

        floatMenu = menuBtn
        windowManager.addView(floatMenu, params)
    }

    private fun toggleEditPanel() {
        if (isPanelVisible) {
            editPanel?.let { windowManager.removeView(it) }
            isPanelVisible = false
            return
        }

        val params = WindowManager.LayoutParams(
            450, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = 20
        }

        editPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#DD000000"))
            setPadding(30, 30, 30, 30)

            addView(TextView(this@KeyMapperService).apply { text = "لوحة التحكم"; setTextColor(Color.WHITE); textSize = 20f })
            
            addView(Button(this@KeyMapperService).apply {
                text = if (isEditMode) "الوضع: تعديل" else "الوضع: لعب"
                setOnClickListener {
                    isEditMode = !isEditMode
                    text = if (isEditMode) "الوضع: تعديل" else "الوضع: لعب"
                    updateButtonsMode()
                }
            })

            addView(Button(this@KeyMapperService).apply {
                text = "إظهار/إخفاء دائرة التحكم"
                setOnClickListener { toggleDPad() }
            })

            addView(Button(this@KeyMapperService).apply {
                text = "تفعيل الماوس (يمين الشاشة)"
                setOnClickListener { addMouseArea() }
            })

            addView(Button(this@KeyMapperService).apply {
                text = "إخفاء الأزرار (لعب نظيف)"
                setOnClickListener { hideAllButtons() }
            })
        }

        windowManager.addView(editPanel, params)
        isPanelVisible = true
    }

    private fun toggleDPad() {
        if (dPadView != null) {
            windowManager.removeView(dPadView)
            dPadView = null
            buttons.removeAll { it.label in listOf("W", "A", "S", "D") }
            return
        }

        val params = WindowManager.LayoutParams(
            400, 400,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = 150; y = 150
        }

        val dPad = FrameLayout(this)
        val shape = GradientDrawable().apply {
            this.shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#22000000"))
            setStroke(4, Color.WHITE)
        }
        dPad.background = shape

        val size = 120
        val btnW = KeyButton(this, KeyEvent.KEYCODE_W, "W", size)
        val btnS = KeyButton(this, KeyEvent.KEYCODE_S, "S", size)
        val btnA = KeyButton(this, KeyEvent.KEYCODE_A, "A", size)
        val btnD = KeyButton(this, KeyEvent.KEYCODE_D, "D", size)

        setupButtonTouch(btnW); setupButtonTouch(btnS); setupButtonTouch(btnA); setupButtonTouch(btnD)

        dPad.addView(btnW, FrameLayout.LayoutParams(size, size).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; topMargin = 10 })
        dPad.addView(btnS, FrameLayout.LayoutParams(size, size).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; bottomMargin = 10 })
        dPad.addView(btnA, FrameLayout.LayoutParams(size, size).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL; leftMargin = 10 })
        dPad.addView(btnD, FrameLayout.LayoutParams(size, size).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; rightMargin = 10 })

        windowManager.addView(dPad, params)
        dPadView = dPad
        buttons.addAll(listOf(btnW, btnS, btnA, btnD))
        updateButtonsMode()
    }

    private fun setupButtonTouch(btn: KeyButton) {
        btn.setOnTouchListener(object : View.OnTouchListener {
            var initialX = 0f; var initialY = 0f; var initialTouchX = 0f; var initialTouchY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (isEditMode) {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = v.x; initialY = v.y
                            initialTouchX = event.rawX; initialTouchY = event.rawY
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (v.parent is FrameLayout && dPadView != null) return true
                            v.x = initialX + (event.rawX - initialTouchX)
                            v.y = initialY + (event.rawY - initialTouchY)
                        }
                    }
                }
                return true
            }
        })
    }

    private fun addMouseArea() {
        if (mouseArea != null) return
        val params = WindowManager.LayoutParams(
            800, WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }

        mouseArea = MouseInterceptorView(this) { dx, dy ->
            if (abs(dx) > 0.5f || abs(dy) > 0.5f) {
                touchService?.injectMouseMove(dx, dy)
            }
        }.apply {
            setBackgroundColor(if(isEditMode) Color.parseColor("#22FF0000") else Color.TRANSPARENT)
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
        }

        windowManager.addView(mouseArea, params)
        mouseArea?.post { (mouseArea as? MouseInterceptorView)?.requestPointerCapture() }
    }

    private fun hideAllButtons() {
        buttons.forEach { it.visibility = View.GONE }
        mouseArea?.visibility = View.GONE
        dPadView?.visibility = View.GONE
        isEditMode = false
    }

    private fun updateButtonsMode() {
        buttons.forEach { it.isEditMode = isEditMode; it.visibility = View.VISIBLE; it.invalidate() }
        mouseArea?.setBackgroundColor(if(isEditMode) Color.parseColor("#22FF0000") else Color.TRANSPARENT)
        dPadView?.visibility = View.VISIBLE
    }

    // ================= محرك التقاط الكيبورد وحقن اللمس =================
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (isEditMode || touchService == null) return false

        val btn = buttons.find { it.keyCode == event.keyCode }
        btn?.let {
            if (event.action == KeyEvent.ACTION_DOWN && !activePointers.containsKey(it.keyCode)) {
                it.isActionPressed = true; it.invalidate()
                addPointer(it)
            } else if (event.action == KeyEvent.ACTION_UP && activePointers.containsKey(it.keyCode)) {
                it.isActionPressed = false; it.invalidate()
                removePointer(it)
            }
            return true
        }
        return super.onKeyEvent(event)
    }

    private fun addPointer(btn: KeyButton) {
        var posX = btn.x
        var posY = btn.y
        if (btn.parent is FrameLayout) {
            val parentParams = (btn.parent as FrameLayout).layoutParams as WindowManager.LayoutParams
            posX += parentParams.x
            posY += parentParams.y
        }
        val coords = MotionEvent.PointerCoords().apply {
            x = posX + (btn.width / 2f)
            y = posY + (btn.height / 2f)
            pressure = 1f; size = 1f
        }
        activePointers[btn.keyCode] = coords
        sendMultiTouch()
    }

    private fun removePointer(btn: KeyButton) {
        activePointers.remove(btn.keyCode)
        sendMultiTouch()
    }

    private fun sendMultiTouch() {
        if (activePointers.isEmpty()) return
        val ids = activePointers.keys.toIntArray()
        val xs = activePointers.values.map { it.x }.toFloatArray()
        val ys = activePointers.values.map { it.y }.toFloatArray()
        
        // إرسال كل الضغطات في نفس اللحظة
        touchService?.injectMultiTouch(ids, xs, ys)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() {
        super.onDestroy()
        floatMenu?.let { windowManager.removeView(it) }
        editPanel?.let { windowManager.removeView(it) }
        buttons.forEach { windowManager.removeView(it) }
        mouseArea?.let { windowManager.removeView(it) }
        dPadView?.let { windowManager.removeView(it) }
    }
}

class MouseInterceptorView(context: Context, private val onMove: (Float, Float) -> Unit) : View(context) {
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE) {
            if (event.actionMasked == MotionEvent.ACTION_HOVER_MOVE || event.actionMasked == MotionEvent.ACTION_MOVE) {
                val dx = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
                val dy = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
                onMove(dx, dy)
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onCapturedPointerEvent(event: MotionEvent): Boolean {
        val dx = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
        val dy = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
        onMove(dx, dy)
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = false
}

class KeyButton(context: Context, val keyCode: Int, val label: String, private val btnSize: Int) : View(context) {
    var isEditMode = true
    var isActionPressed = false

    init { 
        val shape = GradientDrawable().apply {
            this.shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#55000000"))
            setStroke(3, Color.WHITE)
        }
        background = shape
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val paint = android.graphics.Paint().apply {
            color = if (isActionPressed) Color.GREEN else Color.WHITE
            textSize = 35f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText(label, width / 2f, height / 2f + 12, paint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) { 
        setMeasuredDimension(btnSize, btnSize) 
    }
}
