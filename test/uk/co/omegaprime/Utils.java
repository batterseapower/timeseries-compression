package uk.co.omegaprime;

public class Utils {
    public static double[] floatsToDoubles(float[] floats) {
        final double[] doubles = new double[floats.length];
        for (int i = 0; i < doubles.length; i++) {
            doubles[i] = floats[i];
        }
        return doubles;
    }
}
