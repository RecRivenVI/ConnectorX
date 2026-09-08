package io.github.recrivenvi.connectorx.windows

import com.sun.jna.*
import com.sun.jna.platform.win32.BaseTSD.*
import com.sun.jna.win32.StdCallLibrary
import javax.swing.SwingUtilities

/** Hooks only this application's video HWND; never installs global input hooks or injects into Windows. */
internal class WindowsTouch(private val canvas:VideoPanel,private val status:(String)->Unit):AutoCloseable {
    private interface WndProc:StdCallLibrary.StdCallCallback {
        fun invoke(hwnd:Pointer,msg:Int,w:ULONG_PTR,l:LONG_PTR):LONG_PTR
    }
    private interface Api:StdCallLibrary {
        fun RegisterTouchWindow(hwnd:Pointer,flags:Int):Boolean
        fun UnregisterTouchWindow(hwnd:Pointer):Boolean
        fun GetTouchInputInfo(handle:Pointer,count:Int,inputs:Pointer,size:Int):Boolean
        fun CloseTouchInputHandle(handle:Pointer):Boolean
        fun SetWindowLongPtrW(hwnd:Pointer,index:Int,value:Pointer):Pointer?
        fun GetWindowLongPtrW(hwnd:Pointer,index:Int):Pointer?
        fun CallWindowProcW(previous:Pointer,hwnd:Pointer,msg:Int,w:ULONG_PTR,l:LONG_PTR):LONG_PTR
        fun GetMessageExtraInfo():LONG_PTR
        fun GetSystemMetrics(index:Int):Int
        fun ScreenToClient(hwnd:Pointer,point:Pointer):Boolean
    }
    private val api=Native.load("user32",Api::class.java)
    private val hwnd=Native.getComponentPointer(canvas)
    private var previous:Pointer?=null
    private var installed=false
    private val callback=object:WndProc {
        override fun invoke(hwnd:Pointer,msg:Int,w:ULONG_PTR,l:LONG_PTR):LONG_PTR {
            val original=previous ?: return LONG_PTR(0)
            try {
                if(msg==0x0240) { // WM_TOUCH: x/y are hundredths of physical screen pixels; x64 TOUCHINPUT = 48 bytes.
                    val count=(w.toLong() and 65535).toInt();val handle=Pointer(l.toLong())
                    if(count in 1..64) {
                        Memory(count*48L).use { memory ->
                            if(api.GetTouchInputInfo(handle,count,memory,48)) {
                                val contacts=(0 until count).map { i ->val offset=i*48L
                                    Memory(8).use {point->
                                        point.setInt(0,memory.getInt(offset)/100);point.setInt(4,memory.getInt(offset+4)/100)
                                        check(api.ScreenToClient(hwnd,point))
                                        longArrayOf(memory.getInt(offset+16).toLong() and 0xffffffffL,
                                            point.getInt(0).toLong(),point.getInt(4).toLong(),memory.getInt(offset+20).toLong())
                                    }
                                }
                                api.CloseTouchInputHandle(handle)
                                SwingUtilities.invokeLater { contacts.forEach { c->canvas.nativeTouch(c[0]+1,c[1].toDouble(),c[2].toDouble(),c[3].toInt()) } }
                                return LONG_PTR(0)
                            }
                        }
                    }
                }
                // Suppress promoted mouse messages only on this registered touch video window.
                if(msg in 0x0200..0x020E && (api.GetMessageExtraInfo().toLong() and 0xffffff00L)==0xff515700L) return LONG_PTR(0)
                if(msg==0x001F || msg==0x0008) SwingUtilities.invokeLater {canvas.cancelInputs()}
            } catch(_:Exception) {SwingUtilities.invokeLater {canvas.cancelInputs()}}
            return api.CallWindowProcW(original,hwnd,msg,w,l)
        }
    }
    init {
        val proc=CallbackReference.getFunctionPointer(callback)
        previous=api.GetWindowLongPtrW(hwnd,-4)
        if(previous!=null && api.SetWindowLongPtrW(hwnd,-4,proc)!=null) {
            installed=true
            if(api.RegisterTouchWindow(hwnd,0)) status(if(api.GetSystemMetrics(94) and 0x40!=0) "Windows 多指触摸已接入" else "触摸消息已接入；当前未检测到多指硬件")
            else {close();status("Windows 触摸注册失败，鼠标仍可用")}
        } else status("Windows 触摸窗口接入失败，鼠标仍可用")
    }
    override fun close() {
        if(!installed) return
        api.UnregisterTouchWindow(hwnd)
        if(api.GetWindowLongPtrW(hwnd,-4)==CallbackReference.getFunctionPointer(callback)) previous?.let {api.SetWindowLongPtrW(hwnd,-4,it)}
        installed=false
    }
}
