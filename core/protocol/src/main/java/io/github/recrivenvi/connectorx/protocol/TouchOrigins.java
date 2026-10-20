package io.github.recrivenvi.connectorx.protocol;

import java.util.HashSet;
import java.util.Set;

/** Each pointer is admitted only at DOWN; crossing an edge cannot admit or revoke it. */
public final class TouchOrigins {
    private final Set<Integer> accepted = new HashSet<>();
    public boolean down(int id, boolean inside) { if (inside) accepted.add(id); return inside; }
    public boolean move(int id) { return accepted.contains(id); }
    public boolean up(int id) { return accepted.remove(id); }
    public void clear() { accepted.clear(); }
}
