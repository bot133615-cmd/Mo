package com.alphaxk.keymapperpro

import android.os.IBinder
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import java.lang.reflect.Method

class TouchService : ITouchService.Stub() {
    private val inputManager: Any by lazy {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val getService = serviceManager.getMethod("getService", String::class.java)
        val binder = getService.invoke(null, "input") as IBinder
        val stub = Class.forName("android.hardware.input.IInputManager\$Stub")
        stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)!!
    }
    
    private val injectMethod: Method by lazy {
        inputManager.javaClass.getMethod(
            "injectInputEvent",
            android.view.InputEvent::class.java,
            Int::class.javaPrimitiveType
        )
    }

    private var downTime = 0L

    override fun injectMultiTouch(pointerIds: IntArray, xs: FloatArray, ys: FloatArray) {
        val count = pointerIds.size
        if (count == 0) return

        if (downTime == 0L) downTime = SystemClock.uptimeMillis()
        
        val props = Array(count) { i ->
            MotionEvent.PointerProperties().apply { id = pointerIds[i]; toolType = MotionEvent.TOOL_TYPE_FINGER }
        }
        val coords = Array(count) { i ->
            MotionEvent.PointerCoords().apply { x = xs[i]; y = ys[i]; pressure = 1f; size = 1f }
        }

        // الحقن بيتم على هيئة ACTION_MOVE عشان يضمن استمرار الضغطات المتزامنة
        val event = MotionEvent.obtain(
            downTime, SystemClock.uptimeMillis(), 
            MotionEvent.ACTION_MOVE, count, props, coords, 
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
        )

        try { injectMethod.invoke(inputManager, event, 0) } catch (e: Exception) { e.printStackTrace() }
        event.recycle()
    }

    override fun injectMouseMove(dx: Float, dy: Float) {
        // محاكاة تحريك الكاميرا (السحب) في نص الشاشة
        val cx = 900f; val cy = 500f
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 99; toolType = MotionEvent.TOOL_TYPE_FINGER })
        val coords = arrayOf(MotionEvent.PointerCoords().apply { x = cx + (dx * 15); y = cy + (dy * 15); pressure = 1f; size = 1f })
        
        val event = MotionEvent.obtain(
            SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), 
            MotionEvent.ACTION_MOVE, 1, props, coords, 
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
        )
        try { injectMethod.invoke(inputManager, event, 0) } catch (e: Exception) { e.printStackTrace() }
        event.recycle()
    }
}
