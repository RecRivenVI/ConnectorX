package io.github.recrivenvi.connectorx;

/** A heartbeat can keep an active direction alive, but can never restart an expired one. */
final class GlobalScrollLease {
    static final long TIMEOUT_NANOS=2_000_000_000L;
    private int direction;
    private long renewedAt;
    int set(int next,long now,boolean available) {
        if(next < -1 || next > 1)throw new IllegalArgumentException("Invalid scroll direction");
        direction=available?next:0;renewedAt=now;return direction;
    }
    int current(long now,boolean available) {
        if(!available||now-renewedAt>=TIMEOUT_NANOS||now<renewedAt)direction=0;
        return direction;
    }
    int renew(long now,boolean available) {
        int result=current(now,available);renewedAt=now;return result;
    }
}
