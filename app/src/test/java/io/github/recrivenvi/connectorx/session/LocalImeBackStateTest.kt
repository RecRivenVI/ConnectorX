package io.github.recrivenvi.connectorx.session

import org.junit.Assert.*
import org.junit.Test
import io.github.recrivenvi.connectorx.session.LocalImeBackState.Action.*

class LocalImeBackStateTest {
    @Test fun firstBackHidesSecondBackChecksAgainBeforeNavigating() {
        val state=LocalImeBackState()
        assertEquals(CheckIme,state.begin(10))
        assertEquals(Consume,state.begin(20))
        assertEquals(Consume,state.complete(state.requestId,true,100))
        assertEquals(Consume,state.begin(110))
        assertEquals(CheckIme,state.begin(400))
        assertEquals(ForwardBack,state.complete(state.requestId,false,450))
    }
    @Test fun failedHideNeverFallsThroughToBack() {
        val state=LocalImeBackState()
        assertEquals(CheckIme,state.begin(0));val id=state.requestId
        assertTrue(state.failed(id))
        assertEquals(Consume,state.complete(id,false,100))
        assertEquals(CheckIme,state.begin(2000))
    }
    @Test fun reopenedKeyboardIsCheckedOnEveryBack() {
        val state=LocalImeBackState();state.begin(0);state.complete(state.requestId,true,100)
        assertEquals(CheckIme,state.begin(400))
        assertEquals(Consume,state.complete(state.requestId,true,450))
    }
    @Test fun onlyConfirmedAlreadyHiddenCanForwardBack() {
        val state=LocalImeBackState()
        assertEquals(CheckIme,state.begin(0));assertEquals(Consume,state.begin(1))
        assertEquals(ForwardBack,state.complete(state.requestId,false,100))
        assertEquals(Consume,state.complete(state.requestId,false,101))
    }
    @Test fun staleCompletionAfterLeavingPageCannotNavigate() {
        val state=LocalImeBackState();state.begin(0);val old=state.requestId
        state.reset()
        assertEquals(Consume,state.complete(old,false,100))
    }
    @Test fun staleFailuresCannotCancelNewRequest() {
        val state=LocalImeBackState();state.begin(0);val old=state.requestId
        state.reset();state.begin(100)
        assertFalse(state.failed(old));assertTrue(state.isPending(state.requestId))
    }
    @Test fun successfulDismissalCancelsTimeout() {
        val state=LocalImeBackState();state.begin(0);val id=state.requestId
        state.complete(id,true,100);assertFalse(state.failed(id))
    }
}
