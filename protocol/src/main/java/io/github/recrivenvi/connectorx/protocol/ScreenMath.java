package io.github.recrivenvi.connectorx.protocol;

/** Display-coordinate transforms and outside-only frame layout, independent of Android UI. */
public final class ScreenMath {
    /** Recognizes a rectangular perimeter even if it begins midway along an edge or repeats collinear vertices. */
    public static boolean isRectangle(float[] points) {
        if (points.length < 8 || points.length % 2 != 0) return false;
        float left = Float.POSITIVE_INFINITY, top = left, right = Float.NEGATIVE_INFINITY, bottom = right;
        for (int i = 0; i < points.length; i += 2) {
            if (!Float.isFinite(points[i]) || !Float.isFinite(points[i + 1])) return false;
            left = Math.min(left, points[i]); right = Math.max(right, points[i]);
            top = Math.min(top, points[i + 1]); bottom = Math.max(bottom, points[i + 1]);
        }
        if (right <= left || bottom <= top) return false;
        double area = 0;
        for (int i = 0; i < points.length; i += 2) {
            int next = (i + 2) % points.length;
            float x = points[i], y = points[i + 1], nx = points[next], ny = points[next + 1];
            if (x != left && x != right && y != top && y != bottom) return false;
            if (x != nx && y != ny) return false;
            area += (double)x * ny - (double)nx * y;
        }
        return Math.abs(Math.abs(area) / 2 - (double)(right - left) * (bottom - top)) < 0.01;
    }
    public static float[] toLogical(float x, float y, float width, float height, int rotation) {
        switch (rotation) {
            case 0: return new float[]{x, y};
            case 1: return new float[]{y, width - x};
            case 2: return new float[]{width - x, height - y};
            case 3: return new float[]{height - y, x};
            default: throw new IllegalArgumentException("Invalid rotation");
        }
    }
    public static float[] toNatural(float x, float y, float width, float height, int rotation) {
        switch (rotation) {
            case 0: return new float[]{x, y};
            case 1: return new float[]{width - y, x};
            case 2: return new float[]{width - x, height - y};
            case 3: return new float[]{y, height - x};
            default: throw new IllegalArgumentException("Invalid rotation");
        }
    }
    /** Returns content width/height, plus x/y of the outside frame. Border never reduces video pixels. */
    public static float[] fit(float windowWidth, float windowHeight, float margin, float border, float sourceWidth, float sourceHeight) {
        if (!Float.isFinite(windowWidth + windowHeight + margin + border + sourceWidth + sourceHeight)
                || sourceWidth <= 0 || sourceHeight <= 0 || margin < 0 || border < 0) throw new IllegalArgumentException("Invalid geometry");
        float scale = Math.max(0, Math.min((windowWidth - 2 * border) / sourceWidth, (windowHeight - 2 * margin - 2 * border) / sourceHeight));
        float w = sourceWidth * scale, h = sourceHeight * scale;
        return new float[]{w, h, (windowWidth - w - 2 * border) / 2, (windowHeight - h - 2 * border) / 2};
    }
    private ScreenMath() {}
}
