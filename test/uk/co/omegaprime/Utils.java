package uk.co.omegaprime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class Utils {
    public static double[] floatsToDoubles(float[] floats) {
        final double[] doubles = new double[floats.length];
        for (int i = 0; i < doubles.length; i++) {
            doubles[i] = floats[i];
        }
        return doubles;
    }

    public static float[] getExampleData() throws IOException {
        final float[] vod;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(ConditionerTest.class.getResourceAsStream("Vodafone.csv")))) {
            final ArrayList<Float> floats = new ArrayList<Float>();
            String line;
            while ((line = r.readLine()) != null) {
                floats.add(Float.parseFloat(line));
            }

            vod = new float[floats.size()];
            for (int i = 0; i < floats.size(); i++) {
                vod[i] = floats.get(i);
            }
        }
        return vod;
    }
}
