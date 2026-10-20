package io.github.recrivenvi.connectorx.runtime;

import org.junit.Test;
import static org.junit.Assert.*;

public class LocalImeBindingTest {
    private static String state(int uid,int display,boolean shown) {
        return "    mCurClient=ClientState{4a8802e mUid="+uid+" mPid=15081 mSelfReportedDisplayId="+display+"}\n"
                +"      mInputShown="+shown+"\n";
    }
    @Test public void matchesOnlyCurrentClientNotAnIdleConnectedClient() {
        String dump="  ClientState{old mUid=10329 mPid=100 mSelfReportedDisplayId=69}\n"+state(10330,0,true);
        assertFalse(LocalImeBinding.parse(dump).belongsTo(69,10329));
    }
    @Test public void sameDisplayDifferentUidIsNotOwned() {assertFalse(LocalImeBinding.parse(state(10330,69,true)).belongsTo(69,10329));}
    @Test public void sameUidDifferentDisplayIsNotOwned() {assertFalse(LocalImeBinding.parse(state(10329,0,true)).belongsTo(69,10329));}
    @Test public void recognizedCurrentClientCanBeHidden() {
        LocalImeBinding parsed=LocalImeBinding.parse(state(10329,69,true));
        assertTrue(parsed.shown);assertTrue(parsed.belongsTo(69,10329));
    }
    @Test public void alreadyHiddenMayHaveNoClient() {
        assertFalse(LocalImeBinding.parse("mCurClient=null\nmInputShown=false\n").shown);
    }
    @Test public void visibleWithoutClientFailsClosed() {
        assertThrows(IllegalStateException.class,()->LocalImeBinding.parse("mCurClient=null\nmInputShown=true\n"));
    }
    @Test public void ambiguousUsersFailClosed() {
        assertThrows(IllegalStateException.class,()->LocalImeBinding.parse(state(10329,69,true)+state(1010329,90,true)));
    }
    @Test public void missingVisibilityOrChangedRomFormatFailsClosed() {
        assertThrows(IllegalStateException.class,()->LocalImeBinding.parse("curClient=ClientState{mUid=10329 mSelfReportedDisplayId=69}\ninputShown=true"));
    }
    @Test public void ignoresHistoricalShowRequests() {
        assertFalse(LocalImeBinding.parse("history mInputShown=true\n"+state(10329,69,false)).shown);
    }
}
