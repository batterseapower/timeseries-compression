package uk.co.omegaprime;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;

public class ConditionerRandomTest {
    private Random random;

    @Before
    public void setUp() {
        random = new Random();
        final long seed = random.nextLong();
        random.setSeed(seed);
        System.out.println(seed);
    }

    @Test
    public void randomFloatsCanRoundtrip() throws IOException {
        for (int trial = 0; trial < 100; trial++) {
            final float[] xs = new float[random.nextInt(16 * 1024)];
            for (int i = 0; i < xs.length; i++) {
                xs[i] = Float.intBitsToFloat(random.nextInt());
            }

            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Conditioner.writeFloat(xs, baos);

            final float[] ys = new float[xs.length];
            Conditioner.readFloat(ys, new ByteArrayInputStream(baos.toByteArray()));

            assertArrayEquals(xs, ys, 0f);
        }
    }

    @Test
    public void randomDoublesCanRoundtrip() throws IOException {
        for (int trial = 0; trial < 100; trial++) {
            final double[] xs = new double[random.nextInt(16 * 1024)];
            for (int i = 0; i < xs.length; i++) {
                xs[i] = Double.longBitsToDouble(random.nextLong());
            }

            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Conditioner.writeDouble(xs, baos);

            final double[] ys = new double[xs.length];
            Conditioner.readDouble(ys, new ByteArrayInputStream(baos.toByteArray()));

            assertArrayEquals(xs, ys, 0.0);
        }
    }
}
