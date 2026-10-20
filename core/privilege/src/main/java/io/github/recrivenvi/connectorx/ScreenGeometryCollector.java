package io.github.recrivenvi.connectorx;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Path;
import android.graphics.RectF;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.RoundedCorner;
import com.genymobile.scrcpy.FakeContext;
import io.github.recrivenvi.connectorx.protocol.ScreenMath;
import io.github.recrivenvi.connectorx.protocol.Wire;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Read-only live metadata; resource names identify providers, never device models. */
public final class ScreenGeometryCollector {
    /** Developer-only app_process entry point: reads geometry to stdout, with no device file writes or UI operations. */
    public static void main(String[] args) {
        try {
            android.os.Looper.prepareMainLooper();
            com.genymobile.scrcpy.Workarounds.apply();
            System.out.println(collect(args.length == 0 || !"radii".equals(args[0])));
            System.exit(0);
        } catch (Throwable e) { e.printStackTrace(); System.exit(1); }
    }
    static String collect() throws Exception {
        return collect(true);
    }
    static String collect(boolean includeMasks) throws Exception {
        Context context = FakeContext.get();
        DisplayManager manager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        Display display = manager.getDisplay(Display.DEFAULT_DISPLAY);
        if (display == null) throw new IllegalStateException("主显示不可用");
        DisplayMetrics metrics = new DisplayMetrics(); display.getRealMetrics(metrics);
        int rotation = display.getRotation();
        int width = rotation % 2 == 0 ? metrics.widthPixels : metrics.heightPixels;
        int height = rotation % 2 == 0 ? metrics.heightPixels : metrics.widthPixels;
        JSONObject result = new JSONObject().put("schema", 1).put("displayId", 0)
                .put("naturalWidth", width).put("naturalHeight", height).put("rotation", rotation)
                .put("logicalWidth", metrics.widthPixels).put("logicalHeight", metrics.heightPixels).put("dpi", metrics.densityDpi);
        JSONArray issues = new JSONArray();
        float[] radii = new float[4];
        if (Build.VERSION.SDK_INT >= 31) {
            for (int position = 0; position < 4; position++) {
                RoundedCorner corner = display.getRoundedCorner(position);
                if (corner != null) radii[(position + rotation) % 4] = corner.getRadius();
            }
        } else { issues.put("RoundedCorner API 不可用"); }
        JSONArray radiusArray = new JSONArray(); for (float radius : radii) radiusArray.put(radius);
        result.put("radii", radiusArray).put("cornerSource", "Display.getRoundedCorner (radius only)");
        DisplayCutout cutout = display.getCutout();
        result.put("hasCutout", cutout != null && !cutout.getBoundingRects().isEmpty());
        JSONArray bounds = new JSONArray();
        if (cutout != null) for (android.graphics.Rect rect : cutout.getBoundingRects()) {
            bounds.put(new JSONArray(new int[]{rect.left, rect.top, rect.right, rect.bottom}));
        }
        result.put("cutoutBoundsLogical", bounds);
        if (cutout != null) {
            result.put("safeInsetsLogical", new JSONArray(new int[]{cutout.getSafeInsetLeft(), cutout.getSafeInsetTop(),
                    cutout.getSafeInsetRight(), cutout.getSafeInsetBottom()}));
            android.graphics.Insets waterfall = Build.VERSION.SDK_INT >= 30
                    ? cutout.getWaterfallInsets() : android.graphics.Insets.NONE;
            result.put("waterfallInsetsLogical", new JSONArray(new int[]{waterfall.left, waterfall.top, waterfall.right, waterfall.bottom}));
        } else {
            result.put("safeInsetsLogical", new JSONArray(new int[]{0, 0, 0, 0}));
            result.put("waterfallInsetsLogical", new JSONArray(new int[]{0, 0, 0, 0}));
        }
        boolean physicalCutoutPath = false;
        if (Build.VERSION.SDK_INT >= 31 && cutout != null) {
            Path path = cutout.getCutoutPath();
            if (path != null && !path.isEmpty()) {
                // A rectangle can be a layout reservation rather than the actual camera aperture (observed on both test devices).
                float[] approximated = path.approximate(0.25f);
                float[] perimeter = new float[approximated.length / 3 * 2];
                for (int i = 0; i < approximated.length / 3; i++) { perimeter[i * 2] = approximated[i * 3 + 1]; perimeter[i * 2 + 1] = approximated[i * 3 + 2]; }
                boolean rectangle = path.isRect(new RectF()) || ScreenMath.isRectangle(perimeter);
                result.put("cutoutPathKind", rectangle ? "rectangular-reservation-unverified" : "system-path");
                result.put("cutoutContours", contours(path, width, height, rotation));
                physicalCutoutPath = !rectangle;
                if (rectangle) issues.put("DisplayCutout 为矩形预留区，未用它伪造真实挖孔");
            }
        }
        JSONObject preciseCutout = preciseCutout(context, cutout, width, height, rotation);
        if (preciseCutout != null) {
            result.put("preciseCutoutCircleNatural", preciseCutout)
                    .put("cutoutQuality", "oem-systemui-center-plus-framework-width");
        } else if (result.optBoolean("hasCutout")) {
            issues.put("未获得厂商精确挖孔圆心，不绘制挖孔");
        }
        if (!includeMasks) {
            result.put("outlineQuality", "system-radius-g2")
                    .put("cutoutQuality", preciseCutout != null ? "oem-systemui-center-plus-framework-width"
                            : physicalCutoutPath ? "system-path-unverified" : "framework-layout-geometry")
                    .put("maskRenderingEnabled", preciseCutout != null).put("issues", issues);
            if (display.getRotation() != rotation) throw new IllegalStateException("读取期间设备方向发生变化，请重试");
            return result.toString();
        }
        boolean hasTop = false, hasBottom = false;
        try {
            Resources resources = context.getPackageManager().getResourcesForApplication("com.android.systemui");
            int enabledId = resources.getIdentifier("support_round_corner", "bool", "com.android.systemui");
            boolean enabled = enabledId == 0 || resources.getBoolean(enabledId);
            if (enabled) {
                JSONObject top = mask(resources, "screen_round_corner_top", width, height);
                JSONObject bottom = mask(resources, "screen_round_corner_bottom", width, height);
                if (top != null) { result.put("topMask", top); hasTop = true; }
                if (bottom != null) { result.put("bottomMask", bottom); hasBottom = true; }
            } else { issues.put("SystemUI 未启用软件圆角蒙版"); }
        } catch (Exception e) { issues.put("SystemUI 蒙版读取不可用：" + e.getClass().getSimpleName()); }
        boolean detailed = hasTop && hasBottom;
        result.put("outlineQuality", detailed ? "systemui-alpha-contour" : "system-radius-approximation");
        result.put("cutoutQuality", hasTop ? "systemui-alpha-mask" : physicalCutoutPath ? "system-path" : "unavailable");
        if (!detailed) issues.put("未获得完整轮廓蒙版，圆角仅按系统半径近似");
        if (!hasTop && !physicalCutoutPath && result.optBoolean("hasCutout")) issues.put("真实挖孔位置/曲线不可用，本版不猜测绘制");
        result.put("issues", issues);
        if (display.getRotation() != rotation) throw new IllegalStateException("读取期间设备方向发生变化，请重试");
        return result.toString();
    }
    private static JSONObject preciseCutout(Context context, DisplayCutout cutout, int width, int height, int rotation) {
        if (cutout == null) return null;
        try {
            Resources resources = context.getPackageManager().getResourcesForApplication("com.android.systemui");
            int centerId = resources.getIdentifier("cut_out_center_y", "dimen", "com.android.systemui");
            if (centerId == 0) return null;
            float centerY = resources.getDimension(centerId);
            if (!Float.isFinite(centerY) || centerY <= 0 || centerY >= height / 3f) return null;
            RectF selected = null;
            for (android.graphics.Rect rect : cutout.getBoundingRects()) {
                RectF natural = naturalBounds(rect, width, height, rotation);
                if (natural.top <= 1f && natural.width() > 0 && natural.width() <= width / 4f
                        && (selected == null || natural.width() < selected.width())) selected = natural;
            }
            if (selected == null) return null;
            float radius = selected.width() / 2f;
            if (centerY - radius < 0 || centerY + radius > height) return null;
            return new JSONObject().put("centerX", selected.centerX()).put("centerY", centerY).put("radius", radius)
                    .put("source", "com.android.systemui:dimen/cut_out_center_y + DisplayCutout width");
        } catch (Exception ignored) { return null; }
    }
    private static RectF naturalBounds(android.graphics.Rect rect, int width, int height, int rotation) {
        float left = Float.POSITIVE_INFINITY, top = left, right = Float.NEGATIVE_INFINITY, bottom = right;
        float[][] corners = {{rect.left, rect.top}, {rect.right, rect.top}, {rect.right, rect.bottom}, {rect.left, rect.bottom}};
        for (float[] corner : corners) {
            float[] point = ScreenMath.toNatural(corner[0], corner[1], width, height, rotation);
            left = Math.min(left, point[0]); top = Math.min(top, point[1]);
            right = Math.max(right, point[0]); bottom = Math.max(bottom, point[1]);
        }
        return new RectF(left, top, right, bottom);
    }
    private static JSONObject mask(Resources resources, String name, int width, int height) throws Exception {
        int id = resources.getIdentifier(name, "drawable", "com.android.systemui");
        if (id == 0) return null;
        byte[] bytes;
        try (InputStream input = resources.openRawResource(id); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int n;
            while ((n = input.read(buffer)) != -1) { if (output.size() + n > 65536) return null; output.write(buffer, 0, n); }
            bytes = output.toByteArray();
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        // Reject placeholder and mismatched-resolution assets instead of silently stretching them.
        if (bounds.outWidth != width || bounds.outHeight < 2 || bounds.outHeight > height / 2) return null;
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inScaled = false;
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (bitmap == null) return null;
        boolean alpha = bitmap.hasAlpha(); bitmap.recycle(); if (!alpha) return null;
        TypedValue value = new TypedValue(); resources.getValue(id, value, true);
        return new JSONObject().put("source", "com.android.systemui:drawable/" + name).put("resourcePath", String.valueOf(value.string))
                .put("width", bounds.outWidth).put("height", bounds.outHeight).put("sha256", Wire.fingerprint(bytes))
                .put("png", Base64.encodeToString(bytes, Base64.NO_WRAP));
    }
    private static JSONArray contours(Path path, int width, int height, int rotation) throws Exception {
        float[] points = path.approximate(0.25f);
        if (points.length > 24000) throw new IllegalStateException("裁切路径过于复杂");
        JSONArray result = new JSONArray(), contour = new JSONArray(); float lastFraction = -1;
        for (int i = 0; i < points.length; i += 3) {
            if (points[i] == lastFraction && contour.length() > 0) { result.put(contour); contour = new JSONArray(); }
            float[] point = ScreenMath.toNatural(points[i + 1], points[i + 2], width, height, rotation);
            contour.put(point[0]); contour.put(point[1]); lastFraction = points[i];
        }
        if (contour.length() > 0) result.put(contour);
        return result;
    }
    private ScreenGeometryCollector() {}
}
