package io.github.recrivenvi.connectorx.windows

import io.github.recrivenvi.connectorx.protocol.Wire
import java.awt.*
import java.awt.event.*
import java.awt.font.TextHitInfo
import java.awt.geom.Area
import java.awt.im.InputMethodRequests
import java.awt.image.BufferedImage
import java.text.AttributedString
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.*

class VideoPanel(private val session:DesktopSession):Canvas() {
    @Volatile var image:BufferedImage?=null
    var geometry:DesktopGeometry?=null
    var videoWidth=0;private set
    var videoHeight=0;private set
    private val touches=mutableMapOf<Long,Point>()
    private val held=mutableMapOf<Int,Int>()
    private var native:WindowsTouch?=null
    private var composition=""
    private val resizeTimer=Timer(350) {
        syncWindowSize()
    }.apply {isRepeats=false}
    init {
        background=Color.BLACK;isFocusable=true;enableInputMethods(true)
        addComponentListener(object:ComponentAdapter() {
            override fun componentResized(e:ComponentEvent) {cancelInputs();if(width>=128&&height>=128) resizeTimer.restart()}
        })
        addMouseListener(object:MouseAdapter() {
            override fun mousePressed(e:MouseEvent) {
                requestFocusInWindow()
                if(SwingUtilities.isRightMouseButton(e)) {if(hit(e.x,e.y)) session.key(4);return}
                if(SwingUtilities.isLeftMouseButton(e)) touch(0,0,e.x,e.y)
            }
            override fun mouseReleased(e:MouseEvent) {if(SwingUtilities.isLeftMouseButton(e)) touch(1,0,e.x,e.y)}
        })
        addMouseMotionListener(object:MouseMotionAdapter() {override fun mouseDragged(e:MouseEvent) {touch(2,0,e.x,e.y)}})
        addMouseWheelListener { e ->if(hit(e.x,e.y)) {
            val p=coordinates(e.x,e.y)
            if(session.capabilities.optBoolean("scroll")) session.send(Wire.scroll(p.x,p.y,videoWidth,videoHeight,
                if(e.isShiftDown) -e.preciseWheelRotation.toFloat() else 0f,if(e.isShiftDown) 0f else -e.preciseWheelRotation.toFloat()))
            else if(touches.isEmpty()) {touch(0,0,e.x,e.y);touch(2,0,e.x,e.y-e.wheelRotation*80);touch(1,0,e.x,e.y-e.wheelRotation*80)}
        } }
        addFocusListener(object:FocusAdapter() {override fun focusLost(e:FocusEvent) {cancelInputs()}})
        addKeyListener(object:KeyAdapter() {
            override fun keyPressed(e:KeyEvent) {
                // Printable text uses keyTyped/IME to respect the local keyboard layout. Modifier chords use Android keycodes.
                val code=DesktopKeys.code(e) ?: return
                if(!DesktopKeys.shouldSend(e)) return
                val repeat=held.getOrDefault(code,0);held[code]=repeat+1
                session.send(Wire.key(0,code,repeat,DesktopKeys.meta(e)));e.consume()
            }
            override fun keyReleased(e:KeyEvent) {DesktopKeys.code(e)?.let {code->if(held.remove(code)!=null) {session.send(Wire.key(1,code,0,DesktopKeys.meta(e)));e.consume()}}}
            override fun keyTyped(e:KeyEvent) {
                if((e.isAltGraphDown||!e.isControlDown&&!e.isAltDown&&!e.isMetaDown)&&composition.isEmpty()&&e.keyChar!=KeyEvent.CHAR_UNDEFINED&&!Character.isISOControl(e.keyChar)) session.text(e.keyChar.toString())
            }
        })
        addInputMethodListener(object:InputMethodListener {
            override fun inputMethodTextChanged(e:InputMethodEvent) {
                val iterator=e.text
                val text=if(iterator==null) "" else buildString {var c=iterator.first();while(c!=java.text.CharacterIterator.DONE) {append(c);c=iterator.next()}}
                val count=e.committedCharacterCount.coerceIn(0,text.length)
                if(count>0) session.text(text.substring(0,count))
                composition=text.substring(count);e.consume();repaint()
            }
            override fun caretPositionChanged(e:InputMethodEvent) {e.consume()}
        })
    }
    override fun addNotify() {super.addNotify();runCatching {native=WindowsTouch(this) {session.touchSupport=it}}.onFailure {session.touchSupport="触摸接入不可用：${it.message}"}}
    override fun removeNotify() {disposeInput();super.removeNotify()}
    fun disposeInput() {resizeTimer.stop();cancelInputs();native?.close();native=null}
    fun syncWindowSize() {
        if(width<128||height<128) return
        val t=graphicsConfiguration?.defaultTransform
        session.resize((width*(t?.scaleX ?: 1.0)).toInt()/2*2,(height*(t?.scaleY ?: 1.0)).toInt()/2*2)
    }
    fun nativeTouch(id:Long,screenX:Double,screenY:Double,flags:Int) {
        if(!isShowing) return
        val transform=graphicsConfiguration.defaultTransform
        // Windows already converted physical screen coordinates to this HWND's physical client coordinates.
        val x=(screenX/transform.scaleX).roundToInt()
        val y=(screenY/transform.scaleY).roundToInt()
        when {flags and 2!=0->touch(0,id,x,y);flags and 4!=0->touch(1,id,x,y);flags and 1!=0->touch(2,id,x,y)}
    }
    fun setVideoSize(w:Int,h:Int) {if(videoWidth!=w||videoHeight!=h) {cancelInputs();image=null};videoWidth=w;videoHeight=h;repaint()}
    fun boundsForVideo():Rectangle {
        if(videoWidth<=0||videoHeight<=0) return Rectangle()
        val margin=if(session.deviceMirror) 36 else 0;val border=if(session.deviceMirror) 4 else 0
        val scale=max(0.0,min((width-2.0*border)/videoWidth,(height-2.0*(margin+border))/videoHeight))
        val w=(videoWidth*scale).toInt();val h=(videoHeight*scale).toInt()
        return Rectangle((width-w)/2,(height-h)/2,w,h)
    }
    private fun outline(b:Rectangle):Shape=geometry?.takeIf {it.matches(videoWidth,videoHeight)&&session.deviceMirror}
        ?.shape(b.x.toDouble(),b.y.toDouble(),b.width.toDouble(),b.height.toDouble()) ?: b
    fun hit(x:Int,y:Int)=!session.inputBlocked&&videoWidth>0&&
        (!session.deviceMirror||geometry?.matches(videoWidth,videoHeight)==true)&&
        boundsForVideo().contains(x,y)&&outline(boundsForVideo()).contains(x.toDouble(),y.toDouble())
    private fun coordinates(x:Int,y:Int):Point {val b=boundsForVideo();return Point(floor((x-b.x).toDouble()*videoWidth/max(1,b.width)).toInt(),floor((y-b.y).toDouble()*videoHeight/max(1,b.height)).toInt())}
    fun touch(action:Int,id:Long,x:Int,y:Int) {
        if(action==0) {if(!hit(x,y)||touches.size>=10) return} else if(id !in touches) return
        if(session.inputBlocked||session.restarting) {cancelInputs();return}
        val p=coordinates(x,y)
        if(action==1) touches.remove(id) else touches[id]=p
        session.send(Wire.touch(action,id,p.x,p.y,videoWidth,videoHeight))
    }
    fun cancelInputs() {
        val pending=touches.toMap();touches.clear()
        pending.forEach {(id,p)->session.send(Wire.touch(1,id,p.x,p.y,max(1,videoWidth),max(1,videoHeight)))}
        held.keys.toList().forEach {session.send(Wire.key(1,it))};held.clear()
    }
    override fun update(g:Graphics)=paint(g)
    override fun paint(g:Graphics) {
        val draw=g.create() as Graphics2D
        try {
            draw.color=Color.BLACK;draw.fillRect(0,0,width,height)
            val b=boundsForVideo();val shape=outline(b)
            draw.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON)
            if(session.deviceMirror&&b.width>0) {
                val frame=Area(BasicStroke(8f).createStrokedShape(shape));frame.subtract(Area(shape))
                draw.color=Color(110,110,115);draw.fill(frame)
            }
            draw.clip=shape;image?.let {draw.drawImage(it,b.x,b.y,b.width,b.height,null)}
            draw.clip=null
            if(composition.isNotEmpty()) {draw.color=Color.DARK_GRAY;draw.fillRect(0,height-32,width,32);draw.color=Color.WHITE;draw.drawString(composition,12,height-10)}
        } finally {draw.dispose()}
    }
    override fun getInputMethodRequests():InputMethodRequests=object:InputMethodRequests {
        override fun getTextLocation(offset:TextHitInfo?):Rectangle {val p=if(isShowing) locationOnScreen else Point();return Rectangle(p.x+20,p.y+height-32,1,28)}
        override fun getLocationOffset(x:Int,y:Int):TextHitInfo?=null
        override fun getInsertPositionOffset()=0
        override fun getCommittedText(begin:Int,end:Int,attributes:Array<out java.text.AttributedCharacterIterator.Attribute>?)=AttributedString("").iterator
        override fun getCommittedTextLength()=0
        override fun cancelLatestCommittedText(attributes:Array<out java.text.AttributedCharacterIterator.Attribute>?)=null
        override fun getSelectedText(attributes:Array<out java.text.AttributedCharacterIterator.Attribute>?)=AttributedString("").iterator
    }
}

object DesktopKeys {
    fun shouldSend(e:KeyEvent)=!e.isAltGraphDown&&(e.isControlDown||e.isAltDown||e.isMetaDown||e.keyChar==KeyEvent.CHAR_UNDEFINED||Character.isISOControl(e.keyChar))
    fun meta(e:KeyEvent)=(if(e.isShiftDown) 1 else 0) or (if(e.isAltDown) 2 else 0) or (if(e.isControlDown) 4096 else 0) or (if(e.isMetaDown) 65536 else 0)
    fun code(e:KeyEvent):Int?=when(e.keyCode) {
        in KeyEvent.VK_A..KeyEvent.VK_Z->29+e.keyCode-KeyEvent.VK_A
        in KeyEvent.VK_0..KeyEvent.VK_9->7+e.keyCode-KeyEvent.VK_0
        in KeyEvent.VK_F1..KeyEvent.VK_F12->131+e.keyCode-KeyEvent.VK_F1
        in KeyEvent.VK_NUMPAD0..KeyEvent.VK_NUMPAD9->144+e.keyCode-KeyEvent.VK_NUMPAD0
        KeyEvent.VK_ESCAPE->4;KeyEvent.VK_ENTER->66;KeyEvent.VK_BACK_SPACE->67;KeyEvent.VK_DELETE->112
        KeyEvent.VK_TAB->61;KeyEvent.VK_SPACE->62;KeyEvent.VK_UP->19;KeyEvent.VK_DOWN->20;KeyEvent.VK_LEFT->21;KeyEvent.VK_RIGHT->22
        KeyEvent.VK_HOME->122;KeyEvent.VK_END->123;KeyEvent.VK_PAGE_UP->92;KeyEvent.VK_PAGE_DOWN->93;KeyEvent.VK_INSERT->124
        KeyEvent.VK_SHIFT->59;KeyEvent.VK_CONTROL->113;KeyEvent.VK_ALT->57;KeyEvent.VK_META->117
        KeyEvent.VK_COMMA->55;KeyEvent.VK_PERIOD->56;KeyEvent.VK_BACK_QUOTE->68;KeyEvent.VK_MINUS->69;KeyEvent.VK_EQUALS->70
        KeyEvent.VK_OPEN_BRACKET->71;KeyEvent.VK_CLOSE_BRACKET->72;KeyEvent.VK_BACK_SLASH->73;KeyEvent.VK_SEMICOLON->74
        KeyEvent.VK_QUOTE->75;KeyEvent.VK_SLASH->76;KeyEvent.VK_DIVIDE->154;KeyEvent.VK_MULTIPLY->155
        KeyEvent.VK_SUBTRACT->156;KeyEvent.VK_ADD->157;KeyEvent.VK_DECIMAL->158
        else->null
    }
}
