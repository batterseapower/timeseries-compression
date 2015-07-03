package uk.co.omegaprime;

import org.iq80.snappy.SnappyOutputStream;
import org.junit.Test;

import java.io.*;
import java.util.ArrayList;

import static org.junit.Assert.assertTrue;

public class ConditionerTest {
    @Test
    public void conditioningShouldImproveCompression() throws IOException {
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

        final int unconditionedLength;
        {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final DataOutputStream dos = new DataOutputStream(new SnappyOutputStream(baos));
            try {
                for (float x : vod) {
                    dos.writeFloat(x);
                }
            } finally {
                dos.flush();
            }

            unconditionedLength = baos.size();
        }

        final int conditionedLength;
        {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final SnappyOutputStream snos = new SnappyOutputStream(baos);
            try {
                Conditioner.condition(vod, snos);
            } finally {
                snos.flush();
            }

            conditionedLength = baos.size();
        }

        System.out.println("Conditioned data compressed to " + conditionedLength + " vs " + unconditionedLength + " unconditioned");
        assertTrue(conditionedLength < unconditionedLength - 1000); // Unconditioned data is about 22754 bytes long
    }
}
