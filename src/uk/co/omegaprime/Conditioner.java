package uk.co.omegaprime;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Conditioner {
    private Conditioner() {}

    // FIXME: adapt for 23 bits

    private static int twos2unsigned(int x) {
        if (true) return x;

        // Packs positive 2s complement numbers into odd unsigned numbers
        // and negative numbers into even unsigned numbers.
        //
        // Imagine this operating on nibbles rather than ints. Then:
        //   x      = 0100b
        //   result = 1000b - 1
        //          = 0111b (which is odd)
        //
        //   x      = 1110b
        //   (so -1110b = 0001b + 1 = 0010b)
        //   result = -1110b      << 1
        //          = (0001b + 1) << 1
        //          = 0010b       << 1
        //          = 0100b (which is even)
        //
        // Note that this tends to turn 2s complement numbers that are close to 0
        // into unsigned numbers that are close to 0.
        return x <= 0 ? (-x) << 1 : (x << 1) - 1;
    }

    private static int unsigned2twos(int x) {
        if (true) return x;

        // Likewise, if this worked on nibbles we would have:
        //   x      = 0111b
        //   result = (0111b + 1) >>> 1
        //          = 1000b >>> 1
        //          = 0100b
        //
        //   x      = 0100b
        //   result = (0100b & 1000b) | -(0100b >> 1)
        //          = 0000b           | -0010b
        //          = 0000b           | (1101b + 1)
        //          = 0000b           | 1110b
        //          = 1110b
        //
        // So this is indeed the inverse of the above.
        return (x & 1) == 0 ? (x & 0x80000000) | -(x >> 1) : (x + 1) >>> 1;
    }

    private static int descriptor(float x) {
        if (x == 0.0) {
            return 0;
        } else if (Float.isNaN(x)) {
            return 1;
        } else {
            return 2 | ((Float.floatToRawIntBits(x) & 0x80000000) >>> 31);
        }
    }

    public static void condition(float[] xs, OutputStream os) throws IOException {
        // 1. Write descriptors (0, NaN or else positive/negative flag)
        {
            int i;
            for (i = 0; i < (xs.length >>> 2) << 2; i += 4) {
                os.write((descriptor(xs[i + 0]) << 6) |
                         (descriptor(xs[i + 1]) << 4) |
                         (descriptor(xs[i + 2]) << 2) |
                         (descriptor(xs[i + 3]) << 0));
            }

            if (i < xs.length) {
                int acc = 0;
                for (; i < xs.length; i++) {
                    acc = (acc << 2) | descriptor(xs[i]);
                }
                os.write(acc);
            }
        }

        // 2. Write exponents (conveniently exactly byte sized!)
        int defined = 0;
        for (int i = 0; i < xs.length; i++) {
            final float x = xs[i];
            if (x != 0.0 && !Float.isNaN(x)) {
                defined++;
                os.write((Float.floatToRawIntBits(x) & 0x7F800000) >>> 23);
            }
        }

        // 3. Write delta-encoded mantissas
        if (defined > 0) {
            int i;
            int lastMantissa = 0;
            for (i = 0; i < xs.length; i++) {
                final float x = xs[i];
                if (x != 0.0 && !Float.isNaN(x)) {
                    lastMantissa = Float.floatToRawIntBits(x) & 0x007FFFFF;
                    os.write((lastMantissa >>>  0) & 0xFF);
                    os.write((lastMantissa >>>  8) & 0xFF);
                    os.write((lastMantissa >>> 16) & 0x7F);
                    break;
                }
            }

            int j = 0;
            final int[] toWrite = new int[defined - 1];
            for (i = i + 1; i < xs.length; i++) {
                final float x = xs[i];
                if (x != 0.0 && !Float.isNaN(x)) {
                    final int mantissa = Float.floatToRawIntBits(x) & 0x007FFFFF;
                    toWrite[j++] = twos2unsigned(mantissa - lastMantissa);
                    lastMantissa = mantissa;
                }
            }

            for (j = 0; j < toWrite.length; j++) { os.write((toWrite[j] >>>  0) & 0xFF); }
            for (j = 0; j < toWrite.length; j++) { os.write((toWrite[j] >>>  8) & 0xFF); }
            for (j = 0; j < toWrite.length; j++) { os.write((toWrite[j] >>> 16) & 0x7F); }
        }
    }

    private static int undescriptor(float[] xs, int i, int descriptor) {
        switch (descriptor & 0x3) {
            case 0: return 0;
            case 1: xs[i] = Float.NaN; return 0;
            default: xs[i] = (descriptor & 0x1) == 0 ? 1f : -1f; return 1;
        }
    }

    public static void uncondition(float[] xs, InputStream is) throws IOException {
        // 1. Read descriptors
        int defined = 0;
        {
            int i;
            for (i = 0; i < (xs.length >>> 2) << 2; i += 4) {
                final int b = is.read();

                defined += undescriptor(xs, i + 0, b >>> 6);
                defined += undescriptor(xs, i + 1, b >>> 4);
                defined += undescriptor(xs, i + 2, b >>> 2);
                defined += undescriptor(xs, i + 3, b >>> 0);
            }

            if (i < xs.length) {
                final int b = is.read();
                switch (xs.length - i) {
                    case 1:
                        defined += undescriptor(xs, i + 0, b >>> 0);
                        break;
                    case 2:
                        defined += undescriptor(xs, i + 0, b >>> 2);
                        defined += undescriptor(xs, i + 1, b >>> 0);
                        break;
                    default: // 3
                        defined += undescriptor(xs, i + 0, b >>> 4);
                        defined += undescriptor(xs, i + 1, b >>> 2);
                        defined += undescriptor(xs, i + 2, b >>> 0);
                        break;
                }
            }
        }

        if (defined > 0) {
            // 2. Read exponents
            int[] toRead = new int[defined];
            for (int j = 0; j < toRead.length; j++) {
                toRead[j] = is.read() << 23;
            }

            // 3. Read delta-encoded mantissas
            int lastMantissa = ((is.read() & 0xFF) <<  0) |
                               ((is.read() & 0xFF) <<  8) |
                               ((is.read() & 0x7F) << 16);
            toRead[0] |= lastMantissa;

            for (int j = 1; j < toRead.length; j++) { toRead[j] |= (is.read() & 0xFF) <<  0; }
            for (int j = 1; j < toRead.length; j++) { toRead[j] |= (is.read() & 0xFF) <<  8; }
            for (int j = 1; j < toRead.length; j++) { toRead[j] |= (is.read() & 0x7F) << 16; }

            // 4. Stick it all together!
            int j = 0;
            for (int i = 0; i < xs.length; i++) {
                final float x = xs[i];
                if (x != 0.0 && !Float.isNaN(x)) {
                    final int read = toRead[j];
                    if (j++ > 0) { lastMantissa = (lastMantissa + unsigned2twos(read)) & 0x007FFFFF; }
                    xs[i] = Float.intBitsToFloat((x < 0 ? 0x80000000 : 0x00000000) | read & 0x7F800000 | lastMantissa);
                }
            }
        }
    }
}
