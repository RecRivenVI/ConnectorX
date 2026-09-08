package io.github.recrivenvi.connectorx.protocol;

/** Analytic two-cubic corners. Radius is a nominal system size, not a claim about the panel's physical curve. */
public final class G2Corners {
    public static final double SPAN_FACTOR = 1.5;
    private static final double M = 1 - 1 / Math.sqrt(2);
    // Makes curvature stationary at the symmetry join; avoids a dip at the middle of the corner.
    private static final double C = (15 * M + Math.sqrt(12 * M * SPAN_FACTOR - 15 * M * M)) / 6;

    /** Left-edge to top-edge canonical corner: P0,P1,P2,join,Q1,Q2,Q3, with interleaved x/y. */
    public static double[] controls(double radius) {
        if (!Double.isFinite(radius) || radius < 0) throw new IllegalArgumentException("Invalid radius");
        double m = M * radius, c = C * radius, span = SPAN_FACTOR * radius;
        return new double[]{0, span, 0, c, 0, 2*m, m, m, 2*m, 0, c, 0, span, 0};
    }
    /** Uniformly reduce all four radii only if adjacent transition spans would overlap. Order: TL,TR,BR,BL. */
    public static double[] fitRadii(double width, double height, double[] requested) {
        if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0 || requested.length != 4) {
            throw new IllegalArgumentException("Invalid screen geometry");
        }
        for (double radius : requested) if (!Double.isFinite(radius) || radius < 0) throw new IllegalArgumentException("Invalid radius");
        double scale = 1;
        for (int i = 0; i < 4; i++) {
            double sum = SPAN_FACTOR * (requested[i] + requested[(i + 1) % 4]);
            if (sum > 0) scale = Math.min(scale, (i % 2 == 0 ? width : height) / sum);
        }
        double[] fitted = requested.clone();
        for (int i = 0; i < 4; i++) fitted[i] *= scale;
        return fitted;
    }
    private G2Corners() {}
}
