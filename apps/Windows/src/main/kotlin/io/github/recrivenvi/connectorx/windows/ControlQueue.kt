package io.github.recrivenvi.connectorx.windows

import io.github.recrivenvi.connectorx.protocol.Wire
import java.nio.ByteBuffer
import java.util.ArrayDeque

/** Tracks transmitted state, not just UI state, so overflow cannot strand keys or fingers. */
class ControlQueue(private val capacity:Int=256) {
    private val lock=Object(); private val pending=ArrayDeque<ByteArray>()
    private val pointers=mutableMapOf<Long,ByteArray>(); private val keys=mutableSetOf<Int>()
    private var stopped=false
    fun offer(packet:ByteArray):Boolean=synchronized(lock) {
        if(stopped) return false
        val overflow=pending.size>=capacity
        if(overflow) {
            pending.clear()
            pointers.values.forEach { p->pending.add(p.copyOf().apply {this[1]=1;this[22]=0;this[23]=0}) }
            keys.forEach { pending.add(Wire.key(1,it)) }
        } else pending.add(packet)
        lock.notifyAll(); !overflow
    }
    fun take():ByteArray?=synchronized(lock) {
        while(pending.isEmpty()&&!stopped) lock.wait()
        if(stopped) return null
        pending.removeFirst().also { p->
            if(p[0].toInt()==0) {val k=ByteBuffer.wrap(p,2,4).int;if(p[1].toInt()==0) keys.add(k) else keys.remove(k)}
            if(p[0].toInt()==2) {val id=ByteBuffer.wrap(p,2,8).long;if(p[1].toInt()==1) pointers.remove(id) else pointers[id]=p}
            if(p[0].toInt()==21) pointers.clear()
        }
    }
    fun close()=synchronized(lock) {stopped=true;pending.clear();lock.notifyAll()}
}
