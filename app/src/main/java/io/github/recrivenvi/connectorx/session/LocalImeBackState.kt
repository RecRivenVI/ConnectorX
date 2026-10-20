package io.github.recrivenvi.connectorx.session

/** Main-thread state. Virtual-screen IME visibility must be checked by the runtime on every Back. */
internal class LocalImeBackState {
    enum class Action { CheckIme, ForwardBack, Consume }
    private var serial=0L
    private var pending=0L
    private var suppressUntil=0L
    val requestId get()=serial
    fun begin(now:Long):Action {
        if(pending!=0L||now<suppressUntil)return Action.Consume
        pending=++serial
        return Action.CheckIme
    }
    fun complete(id:Long,dismissed:Boolean,now:Long):Action {
        if(!isPending(id))return Action.Consume
        pending=0
        if(!dismissed)return Action.ForwardBack
        suppressUntil=now+250 // Do not let trailing callbacks dismiss the target Activity too.
        return Action.Consume
    }
    fun isPending(id:Long)=pending!=0L&&pending==id
    fun failed(id:Long):Boolean {
        if(!isPending(id))return false
        pending=0
        return true
    }
    fun reset(){pending=0;suppressUntil=0;serial++}
}
