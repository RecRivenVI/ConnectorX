package io.github.recrivenvi.connectorx.protocol;

import org.junit.Test;
import static org.junit.Assert.*;

public class G2CornersTest {
    private double[] derivative(double[] p, int segment, double t, int order) {
        int offset = segment * 6;
        double[] d = new double[2];
        for (int axis = 0; axis < 2; axis++) {
            double a=p[offset+axis], b=p[offset+2+axis], c=p[offset+4+axis], e=p[offset+6+axis];
            d[axis] = order == 1 ? 3*((1-t)*(1-t)*(b-a)+2*(1-t)*t*(c-b)+t*t*(e-c))
                    : 6*((1-t)*(c-2*b+a)+t*(e-2*c+b));
        }
        return d;
    }
    private double curvature(double[] p, int segment, double t) {
        double[] d = derivative(p,segment,t,1), dd = derivative(p,segment,t,2);
        return (d[0]*dd[1]-d[1]*dd[0])/Math.pow(d[0]*d[0]+d[1]*d[1],1.5);
    }
    @Test public void joinsStraightEdgesWithZeroCurvatureAndCorrectTangents() {
        for (double radius : new double[]{1,147,170,1000}) {
            double[] p=G2Corners.controls(radius);
            assertEquals(0,curvature(p,0,0),1e-12);
            assertEquals(0,curvature(p,1,1),1e-12);
            double[] entry=derivative(p,0,0,1), exit=derivative(p,1,1,1);
            assertEquals(0,entry[0],0); assertTrue(entry[1]<0);
            assertEquals(0,exit[1],0); assertTrue(exit[0]>0);
        }
    }
    @Test public void middleJoinHasEqualTangentsAndCurvature() {
        for (double radius : new double[]{1,147,170,1000}) {
            double[] p=G2Corners.controls(radius);
            assertArrayEquals(derivative(p,0,1,1),derivative(p,1,0,1),1e-10);
            assertEquals(curvature(p,0,1),curvature(p,1,0),1e-12);
            assertEquals(1.3165313346079126/radius,curvature(p,0,1),1e-10);
        }
    }
    @Test public void curvatureRisesSmoothlyToMiddleAndCurveStaysRegular() {
        double[] p=G2Corners.controls(170); double last=-1;
        for(int i=0;i<=1000;i++) {
            double t=i/1000.; double k=curvature(p,0,t);
            assertTrue(k>=last-1e-12); last=k;
            double[] d=derivative(p,0,t,1); assertTrue(d[0]*d[0]+d[1]*d[1]>0);
            assertEquals(k,curvature(p,1,1-t),1e-10);
        }
    }
    @Test public void retainsNominalDiagonalInsetAndExtendsTangency() {
        double[] p=G2Corners.controls(170);
        assertEquals(255,p[1],0); assertEquals(255,p[12],0);
        assertEquals(170*(1-1/Math.sqrt(2)),p[6],1e-12); assertEquals(p[6],p[7],0);
    }
    @Test public void normalDeviceRadiiRemainUnchangedAndLargeRadiiFitAllEdges() {
        assertArrayEquals(new double[]{170,170,170,170},G2Corners.fitRadii(1200,2608,new double[]{170,170,170,170}),0);
        double[] p=G2Corners.fitRadii(200,300,new double[]{170,147,80,90});
        for(int i=0;i<4;i++) assertTrue((p[i]+p[(i+1)%4])*G2Corners.SPAN_FACTOR <= (i%2==0?200:300)+1e-9);
    }
    @Test public void zeroAndInvalidRadiiAreExplicit() {
        assertArrayEquals(new double[14],G2Corners.controls(0),0);
        for(double radius:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY}) {
            try {G2Corners.controls(radius);fail();} catch(IllegalArgumentException expected) {}
        }
    }
}
