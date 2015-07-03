package uk.co.omegaprime;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;

public class ConditionerRandomTest {
    @Test
    public void randomFloatsCanRoundtrip() throws IOException {
        final Random random = new Random();
        final long seed = random.nextLong();
        random.setSeed(seed);
        System.out.println(seed);

        for (int trial = 0; trial < 100; trial++) {
            final float[] xs = new float[random.nextInt(16 * 1024)];
            for (int i = 0; i < xs.length; i++) {
                xs[i] = Float.intBitsToFloat(random.nextInt());
            }

            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Conditioner.condition(xs, baos);

            final float[] ys = new float[xs.length];
            Conditioner.uncondition(ys, new ByteArrayInputStream(baos.toByteArray()));

            assertArrayEquals(xs, ys, 0f);
        }
    }
}
