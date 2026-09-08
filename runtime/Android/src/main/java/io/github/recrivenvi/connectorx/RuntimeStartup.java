package io.github.recrivenvi.connectorx;

import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Deadline shared by both channels; the poll operation itself must be non-blocking/bounded. */
final class RuntimeStartup {
    interface Poll<T> { T poll(int timeoutMs) throws Exception; }
    static <T> T await(BooleanSupplier alive, LongSupplier clock, long deadline, Poll<T> poll) throws Exception {
        long remaining;
        while ((remaining = deadline - clock.getAsLong()) > 0) {
            if (!alive.getAsBoolean()) throw new IOException("scrcpy server 已退出，请查看被控端日志");
            T result = poll.poll((int) Math.min(100, remaining));
            if (result != null) return result;
        }
        throw new IOException("等待 scrcpy 本地连接超时（10 秒）");
    }
    private RuntimeStartup() {}
}
