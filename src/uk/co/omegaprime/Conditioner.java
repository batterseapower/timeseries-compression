package uk.co.omegaprime;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Conditioner {
    private Conditioner() {}

    // https://graphics.stanford.edu/~seander/bithacks.html#VariableSignExtend
    private static int signExtend23(int x) {
        final int m = 1 << 22;   // Pre-computed mask
        x = x & ((1 << 23) - 1); // Ensure bits in x above bit 23 are already zero.
        return (x ^ m) - m;
    }

    // Zig-zag encoding, as seen in Protocol buffers (https://developers.google.com/protocol-buffers/docs/encoding)
    // Numbers >= 0 are encoded as even numbers and numbers < are encoded as odd numbers. Numbers with a small absolute
    // value in a 2s complement sense are small in an unsigned sense after this transformation.
    //
    // Adapted for 23 bit integers (i.e. float mantissas)

    static int twos2unsigned23(int x) {
        return ((x << 1) ^ (signExtend23(x) >> 22)) & 0x7FFFFF;
    }

    static int unsigned2twos23(int x) {
        return (x & 1) == 0 ?   (x >> 1) & 0x3FFFFF
                            : (~(x >> 1) & 0x3FFFFF) | 0x400000;
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
                    toWrite[j++] = twos2unsigned23(mantissa - lastMantissa);
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
                    if (j++ > 0) { lastMantissa = (lastMantissa + unsigned2twos23(read)) & 0x007FFFFF; }
                    xs[i] = Float.intBitsToFloat((x < 0 ? 0x80000000 : 0x00000000) | read & 0x7F800000 | lastMantissa);
                }
            }
        }
    }
}
