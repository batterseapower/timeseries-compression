package uk.co.omegaprime;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class ConditionerExamplesTest {
    private static final float[] SINGLETON_EXAMPLES = new float[] { 0f, 1f -1f, 2f, -2f, 3f, -3f, Float.NaN, Float.MIN_VALUE, Float.MAX_VALUE, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY };
    private static final float[][] COMPOUND_EXAMPLES = new float[][] {
        new float[] { 2f, 2f, 2f, 2f }, // Easy?
        new float[] { -1f, 0f, 1f, 2f },
        new float[] { 2f, 3f, 2f },
        new float[] { 2f, 3.9999999f, 2f }, // Try to stress the mantissa delta coding
    };

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        final ArrayList<Object[]> result = new ArrayList<Object[]>();
        for (float x : SINGLETON_EXAMPLES) {
            result.add(new Object[] { new float[] { x } });
        }
        for (float[] xs : COMPOUND_EXAMPLES) {
            result.add(new Object[] { xs });
        }
        return result;
    }

    private final float[] floats;
    private final double[] doubles;

    public ConditionerExamplesTest(float[] floats) {
        this.floats = floats;
        this.doubles = Utils.floatsToDoubles(floats);
    }

    @Test
    public void conditionUnconditionFloatIsRoundTrip() throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Conditioner.condition(floats, baos);

        final float[] ys = new float[floats.length];
        Conditioner.uncondition(ys, new ByteArrayInputStream(baos.toByteArray()));

        assertArrayEquals(floats, ys, 0f);
    }

    @Test
    public void conditionUnconditionDoubleIsRoundTrip() throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Conditioner.condition(doubles, baos);

        final double[] ys = new double[doubles.length];
        Conditioner.uncondition(ys, new ByteArrayInputStream(baos.toByteArray()));

        assertArrayEquals(doubles, ys, 0.0);
    }
}
