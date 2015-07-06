package uk.co.omegaprime;

import jdk.nashorn.internal.ir.annotations.Ignore;
import org.iq80.snappy.SnappyOutputStream;
import org.junit.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConditionerTest {
    private static File createFile(String which) throws IOException {
        final File tempFile = File.createTempFile(which, ".bin");
        System.out.println("Writing to " + tempFile);
        return tempFile;
    }

    @Test
    public void smallAbsoluteNumbersEncodeToSmallIntegers() {
        assertTrue(Conditioner.twos2unsigned23(1) < 10);
        assertTrue(Conditioner.twos2unsigned23(-1) < 10);
        assertTrue(Conditioner.twos2unsigned52(1) < 10);
        assertTrue(Conditioner.twos2unsigned52(-1) < 10);
    }

    @Test
    public void twos2unsigned23() {
        for (int i = 0; i < 0x800000; i++) {
            final int u = Conditioner.twos2unsigned23(i);
            assertEquals(u, u & 0x7FFFFF);
            final int v = Conditioner.unsigned2twos23(u);
            assertEquals(Integer.toBinaryString(i) + " -> " + Integer.toBinaryString(u) + " -> " + Integer.toBinaryString(v), i, v);
        }
    }

    @Test
    public void twos2unsigned52() {
        for (long i = 0; i < 0x80000000000000L; i += 0x100000000L) {
            final long u = Conditioner.twos2unsigned52(i);
            assertEquals(u, u & 0x7FFFFF);
            final long v = Conditioner.unsigned2twos52(u);
            assertEquals(Long.toBinaryString(i) + " -> " + Long.toBinaryString(u) + " -> " + Long.toBinaryString(v), i, v);
        }
    }

    @Test
    @Ignore
    public void writeSampleFiles() throws IOException {
        final float[] vod = getExampleData();

        try (FileOutputStream fos = new FileOutputStream(createFile("unconditioned-floats"))) {
            writeUnconditioned(vod, fos);
        }

        try (FileOutputStream fos = new FileOutputStream(createFile("conditioned-floats"))) {
            Conditioner.condition(vod, fos);
        }

        try (FileOutputStream fos = new FileOutputStream(createFile("unconditioned-doubles"))) {
            writeUnconditioned(Utils.floatsToDoubles(vod), fos);
        }

        try (FileOutputStream fos = new FileOutputStream(createFile("conditioned-doubles"))) {
            Conditioner.condition(Utils.floatsToDoubles(vod), fos);
        }
    }

    @Test
    public void conditioningShouldImproveCompression() throws IOException {
        // Unconditioned data is about 22754 bytes long, conditioned 21252 as of time of writing
        assertConditioningBetter(getExampleData(), 1000);
    }

    @Test
    public void conditioningShouldImproveCompressionWithZeros() throws IOException {
        float[] vod = getExampleData();
        float[] returns = new float[vod.length];
        returns[0] = 1f;
        for (int i = 1; i < vod.length; i++) {
            returns[i] = (vod[i] / vod[i - 1]) - 1f;
        }

        assertConditioningBetter(returns, 4000); // 23608 vs 27810 currently
    }

    @Test
    public void conditioningShouldImproveCompressionWithNaNs() throws IOException {
        final Random random = new Random();
        float[] vod = getExampleData();
        for (int i = 0; i < vod.length; i++) {
            if (random.nextDouble() < 0.05) vod[i] = Float.NaN;
        }

        assertConditioningBetter(vod, 1000); // (roughly) 20567 vs 22443 currently
    }

    private void assertConditioningBetter(float[] vod, int improvement) throws IOException {
        for (boolean asFloats : new boolean[] { true, false }) {
            final int unconditionedLength;
            {
                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                final SnappyOutputStream sos = new SnappyOutputStream(baos);
                try {
                    if (asFloats) {
                        writeUnconditioned(vod, sos);
                    } else {
                        writeUnconditioned(Utils.floatsToDoubles(vod), sos);
                    }
                } finally {
                    sos.flush();
                }

                unconditionedLength = baos.size();
            }

            final int conditionedLength;
            {
                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                final SnappyOutputStream sos = new SnappyOutputStream(baos);
                try {
                    if (asFloats) {
                        Conditioner.condition(vod, sos);
                    } else {
                        Conditioner.condition(Utils.floatsToDoubles(vod), sos);
                    }
                } finally {
                    sos.flush();
                }

                conditionedLength = baos.size();
            }

            System.out.println("Conditioned " + (asFloats ? "float" : "double") + " data compressed to " + conditionedLength + " vs " + unconditionedLength + " unconditioned");
            assertTrue(conditionedLength < unconditionedLength - improvement);
        }
    }

    private void writeUnconditioned(float[] vod, OutputStream os) throws IOException {
        final DataOutputStream dos = new DataOutputStream(os);
        try {
            for (float x : vod) {
                dos.writeFloat(x);
            }
        } finally {
            dos.flush();
        }
    }

    private void writeUnconditioned(double[] vod, OutputStream os) throws IOException {
        final DataOutputStream dos = new DataOutputStream(os);
        try {
            for (double x : vod) {
                dos.writeDouble(x);
            }
        } finally {
            dos.flush();
        }
    }

    private static float[] getExampleData() throws IOException {
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
