package uk.co.omegaprime;

import sun.misc.IOUtils;

import java.io.*;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class Conditioner {
    private Conditioner() {}

    // https://graphics.stanford.edu/~seander/bithacks.html#VariableSignExtend
    private static short signExtend11(short x) {
        final short m = (short)(1 << 10); // Pre-computed mask
        x = (short)(x & ((1 << 11) - 1)); // Ensure bits in x above bit 11 are already zero.
        return (short)((x ^ m) - m);
    }

    private static int signExtend23(int x) {
        final int m = 1 << 22;   // Pre-computed mask
        x = x & ((1 << 23) - 1); // Ensure bits in x above bit 23 are already zero.
        return (x ^ m) - m;
    }

    private static int signExtend32(int x) {
        return x;
    }

    private static long signExtend52(long x) {
        final long m = 1L << 51;  // Pre-computed mask
        x = x & ((1L << 52) - 1); // Ensure bits in x above bit 52 are already zero.
        return (x ^ m) - m;
    }

    private static long signExtend64(long x) {
        return x;
    }

    // Zig-zag encoding, as seen in Protocol buffers (https://developers.google.com/protocol-buffers/docs/encoding)
    // Numbers >= 0 are encoded as even numbers and numbers < are encoded as odd numbers. Numbers with a small absolute
    // value in a 2s complement sense are small in an unsigned sense after this transformation.
    //
    // Adapted for 23 & 52 bit integers (i.e. float/double mantissas)

    static short twos2unsigned11(short x) {
        return (short)(((x << 1) ^ (signExtend11(x) >> 10)) & 0x7FF);
    }

    static short unsigned2twos11(short x) {
        return (short)((x & 1) == 0 ?   (x >> 1) & 0x3FF
                                    : (~(x >> 1) & 0x3FF) | 0x400);
    }

    static int twos2unsigned23(int x) {
        return ((x << 1) ^ (signExtend23(x) >> 22)) & 0x7FFFFF;
    }

    static int unsigned2twos23(int x) {
        return (x & 1) == 0 ?   (x >> 1) & 0x3FFFFF
                            : (~(x >> 1) & 0x3FFFFF) | 0x400000;
    }

    static int twos2unsigned32(int x) {
        return (x << 1) ^ (signExtend32(x) >> 31);
    }

    static int unsigned2twos32(int x) {
        return (x & 1) == 0 ?   (x >> 1) & 0x7FFFFFFF
                            : (~(x >> 1) & 0x7FFFFFFF) | 0x80000000;
    }

    static long twos2unsigned52(long x) {
        return ((x << 1) ^ (signExtend52(x) >> 51)) & 0xFFFFFFFFFFFFFL;
    }

    static long unsigned2twos52(long x) {
        return (x & 1) == 0 ?   (x >> 1) & 0x7FFFFFFFFFFFFL
                            : (~(x >> 1) & 0x7FFFFFFFFFFFFL) | 0x8000000000000L;
    }

    static long twos2unsigned64(long x) {
        return (x << 1) ^ (signExtend64(x) >> 63);
    }

    static long unsigned2twos64(long x) {
        return (x & 1) == 0 ?   (x >> 1) & 0x7FFFFFFFFFFFFFFFL
                            : (~(x >> 1) & 0x7FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
    }

    private static int descriptor(int[] definedByRef, float x) {
        if (x == 0f) {
            return 0;
        } else if (Float.isNaN(x)) {
            return 1;
        } else {
            definedByRef[0]++;
            return 2 | ((Float.floatToRawIntBits(x) & 0x80000000) >>> 31);
        }
    }

    private static int descriptor(int[] definedByRef, double x) {
        if (x == 0.0) {
            return 0;
        } else if (Double.isNaN(x)) {
            return 1;
        } else {
            definedByRef[0]++;
            return 2 | (int)((Double.doubleToRawLongBits(x) & 0x8000000000000000L) >>> 63);
        }
    }

    public interface Writer<T> {
        public void write(T from, OutputStream os) throws IOException;
    }

    public interface Reader<T> {
        public void read(T into, InputStream is) throws IOException;
    }

    public static Writer<float[]> writeFloatLiteral(int[] codec) {
        return (float[] xs, OutputStream os) -> {
            final int[] bits = new int[xs.length];
            for (int i = 0; i < bits.length; i++) {
                bits[i] = Float.floatToRawIntBits(xs[i]);
            }
            columnarWriteInt(codec).write(bits, os);
        };
    }

    public static Reader<float[]> readFloatLiteral(int[] codec) {
        return (float[] xs, InputStream is) -> {
            final int[] bits = new int[xs.length];
            columnarReadInt(codec).read(bits, is);
            for (int i = 0; i < bits.length; i++) {
                xs[i] = Float.intBitsToFloat(bits[i]);
            }
        };
    }

    public static Writer<double[]> writeDoubleLiteral(int[] codec) {
        return (double[] xs, OutputStream os) -> {
            final long[] bits = new long[xs.length];
            for (int i = 0; i < bits.length; i++) {
                bits[i] = Double.doubleToRawLongBits(xs[i]);
            }
            columnarWriteLong(codec).write(bits, os);
        };
    }

    public static Reader<double[]> readDoubleLiteral(int[] codec) {
        return (double[] xs, InputStream is) -> {
            final long[] bits = new long[xs.length];
            columnarReadLong(codec).read(bits, is);
            for (int i = 0; i < bits.length; i++) {
                xs[i] = Double.longBitsToDouble(bits[i]);
            }
        };
    }

    public static Writer<float[]> writeFloatDelta(int[] codec) {
        return (float[] xs, OutputStream os) -> {
            if (xs.length == 0) return;

            int lastBits = Float.floatToRawIntBits(xs[0]);
            new DataOutputStream(os).writeInt(lastBits);

            final int[] toWrite = new int[xs.length - 1];
            for (int i = 1; i < xs.length; i++) {
                final int bits = Float.floatToRawIntBits(xs[i]);
                toWrite[i - 1] = twos2unsigned32(bits - lastBits);
                lastBits = bits;
            }

            columnarWriteInt(codec).write(toWrite, os);
        };
    }

    public static Reader<float[]> readFloatDelta(int[] codec) {
        return (float[] xs, InputStream is) -> {
            if (xs.length == 0) return;

            int lastBits = new DataInputStream(is).readInt();
            xs[0] = Float.intBitsToFloat(lastBits);

            final int[] read = new int[xs.length - 1];
            columnarReadInt(codec).read(read, is);

            for (int i = 1; i < xs.length; i++) {
                xs[i] = Float.intBitsToFloat(lastBits = lastBits + unsigned2twos32(read[i - 1]));
            }
        };
    }

    public static Writer<double[]> writeDoubleDelta(int[] codec) {
        return (double[] xs, OutputStream os) -> {
            if (xs.length == 0) return;

            long lastBits = Double.doubleToRawLongBits(xs[0]);
            new DataOutputStream(os).writeLong(lastBits);

            final long[] toWrite = new long[xs.length - 1];
            for (int i = 1; i < xs.length; i++) {
                final long bits = Double.doubleToRawLongBits(xs[i]);
                toWrite[i - 1] = twos2unsigned64(bits - lastBits);
                lastBits = bits;
            }

            columnarWriteLong(codec).write(toWrite, os);
        };
    }

    public static Reader<double[]> readDoubleDelta(int[] codec) {
        return (double[] xs, InputStream is) -> {
            if (xs.length == 0) return;

            long lastBits = new DataInputStream(is).readLong();
            xs[0] = Double.longBitsToDouble(lastBits);

            final long[] read = new long[xs.length - 1];
            columnarReadLong(codec).read(read, is);

            for (int i = 1; i < xs.length; i++) {
                xs[i] = Double.longBitsToDouble(lastBits = lastBits + unsigned2twos64(read[i - 1]));
            }
        };
    }

    public static Writer<byte[]> writeFloatExponentsLiteral() {
        return (byte[] exponents, OutputStream os) -> os.write(exponents);
    }

    public static Reader<byte[]> readFloatExponentsLiteral() {
        return (byte[] exponents, InputStream os) -> {
            int off = 0;
            int read;
            do {
                read = os.read(exponents, off, exponents.length - off);
                off += read;
            } while (read > 0);
        };
    }

    public static Writer<short[]> writeDoubleExponentsLiteral(int[] codec) {
        return (short[] exponents, OutputStream os) -> {
            columnarWriteShort(codec).write(exponents, os);
        };
    }

    public static Reader<short[]> readDoubleExponentsLiteral(int[] codec) {
        return (short[] exponents, InputStream is) -> {
            columnarReadShort(codec).read(exponents, is);
        };
    }

    public static Writer<byte[]> writeFloatExponentsDelta() {
        return (byte[] exponents, OutputStream os) -> {
            if (exponents.length == 0) return;

            byte lastExponent = exponents[0];
            os.write(((int)lastExponent) & 0xFF);

            for (int i = 1; i < exponents.length; i++) {
                final byte exponent = exponents[i];
                os.write((exponent - lastExponent) & 0xFF);
                lastExponent = exponent;
            }
        };
    }

    public static Reader<byte[]> readFloatExponentsDelta() {
        return (byte[] exponents, InputStream os) -> {
            if (exponents.length == 0) return;

            byte lastExponent = exponents[0] = (byte)os.read();
            for (int i = 1; i < exponents.length; i++) {
                final int delta = os.read();
                lastExponent = exponents[i] = (byte)((lastExponent + delta) & 0xFF);
            }
        };
    }

    public static Writer<short[]> writeDoubleExponentsDelta(int[] codec) {
        return (short[] exponents, OutputStream os) -> {
            if (exponents.length == 0) return;

            short lastExponent = exponents[0];
            os.write((lastExponent >>>  0) & 0xFF);
            os.write((lastExponent >>>  8) & 0x07);

            final short[] toWrite = new short[exponents.length - 1];
            for (int i = 1; i < exponents.length; i++) {
                final short exponent = exponents[i];
                toWrite[i - 1] = twos2unsigned11((short)((exponent - lastExponent) & 0x7FF));
                lastExponent = exponent;
            }

            columnarWriteShort(codec).write(toWrite, os);
        };
    }

    public static Reader<short[]> readDoubleExponentsDelta(int[] codec) {
        return (short[] exponents, InputStream is) -> {
            if (exponents.length == 0) return;

            short lastExponent = exponents[0] = (short)(is.read() | (is.read() << 8));

            final short[] read = new short[exponents.length - 1];
            columnarReadShort(codec).read(read, is);

            for (int i = 1; i < exponents.length; i++) {
                lastExponent = exponents[i] = (short)((lastExponent + unsigned2twos11(read[i - 1])) & 0x7FF);
            }
        };
    }

    public static Writer<int[]> writeFloatMantissasLiteral(int[] codec) {
        return columnarWriteInt(codec);
    }

    public static Reader<int[]> readFloatMantissasLiteral(int[] codec) {
        return columnarReadInt(codec);
    }

    public static Writer<int[]> writeFloatMantissasDelta(int[] codec) {
        return (int[] mantissas, OutputStream os) -> {
            if (mantissas.length == 0) return;

            int lastMantissa = mantissas[0];
            os.write((lastMantissa >>>  0) & 0xFF);
            os.write((lastMantissa >>>  8) & 0xFF);
            os.write((lastMantissa >>> 16) & 0x7F);

            final int[] toWrite = new int[mantissas.length - 1];
            for (int i = 1; i < mantissas.length; i++) {
                final int mantissa = mantissas[i];
                toWrite[i - 1] = twos2unsigned23(mantissa - lastMantissa);
                lastMantissa = mantissa;
            }

            columnarWriteInt(codec).write(toWrite, os);
        };
    }

    public static Reader<int[]> readFloatMantissasDelta(int[] codec) {
        return (int[] mantissas, InputStream is) -> {
            if (mantissas.length == 0) return;

            int lastMantissa = mantissas[0] = is.read() | (is.read() << 8) | (is.read() << 16);

            final int[] read = new int[mantissas.length - 1];
            columnarReadInt(codec).read(read, is);

            for (int i = 1; i < mantissas.length; i++) {
                lastMantissa = mantissas[i] = ((lastMantissa + unsigned2twos23(read[i - 1])) & 0x7FFFFF);
            }
        };
    }

    public static Writer<long[]> writeDoubleMantissasLiteral(int[] codec) {
        return columnarWriteLong(codec);
    }

    public static Reader<long[]> readDoubleMantissasLiteral(int[] codec) {
        return columnarReadLong(codec);
    }

    public static Writer<long[]> writeDoubleMantissasDelta(int[] codec) {
        return (long[] mantissas, OutputStream os) -> {
            if (mantissas.length == 0) return;

            long lastMantissa = mantissas[0];
            os.write((int)((lastMantissa >>>  0) & 0xFF));
            os.write((int)((lastMantissa >>>  8) & 0xFF));
            os.write((int)((lastMantissa >>> 16) & 0xFF));
            os.write((int)((lastMantissa >>> 24) & 0xFF));
            os.write((int)((lastMantissa >>> 32) & 0xFF));
            os.write((int)((lastMantissa >>> 40) & 0xFF));
            os.write((int)((lastMantissa >>> 48) & 0x0F));

            final long[] toWrite = new long[mantissas.length - 1];
            for (int i = 1; i < mantissas.length; i++) {
                final long mantissa = mantissas[i];
                toWrite[i - 1] = twos2unsigned52(mantissa - lastMantissa);
                lastMantissa = mantissa;
            }

            columnarWriteLong(codec).write(toWrite, os);
        };
    }

    public static Reader<long[]> readDoubleMantissasDelta(int[] codec) {
        return (long[] mantissas, InputStream is) -> {
            if (mantissas.length == 0) return;

            long lastMantissa = mantissas[0] = ((long)is.read() <<  0)
                                             | ((long)is.read() <<  8)
                                             | ((long)is.read() << 16)
                                             | ((long)is.read() << 24)
                                             | ((long)is.read() << 32)
                                             | ((long)is.read() << 40)
                                             | ((long)is.read() << 48);

            final long[] read = new long[mantissas.length - 1];
            columnarReadLong(codec).read(read, is);

            for (int i = 1; i < mantissas.length; i++) {
                lastMantissa = mantissas[i] = ((lastMantissa + unsigned2twos52(read[i - 1])) & 0xFFFFFFFFFFFFFL);
            }
        };

    }

    private static List<Integer> insertAt(List<Integer> xs, int ix, int value) {
        final List<Integer> result = new ArrayList<>(xs);
        result.add(ix, value);
        return result;
    }

    // For length == 3, valid codecs are [1, 1, 1], [1, 2], [2, 1], [3] (numbers must be > 0 and sum to 3)
    public static List<int[]> validCodecs(int length) {
        Set<List<Integer>> work = new LinkedHashSet<>(Arrays.<List<Integer>>asList(new ArrayList<Integer>()));
        for (int n = 0; n < length; n++) {
            final Set<List<Integer>> newWork = new LinkedHashSet<>();

            for (List<Integer> codec : work) {
                for (int i = 0; i <= codec.size(); i++) {
                    newWork.add(insertAt(codec, i, 1));
                }

                for (int i = 0; i < codec.size(); i++) {
                    List<Integer> clone = new ArrayList<>(codec);
                    clone.set(i, clone.get(i) + 1);
                    newWork.add(clone);
                }
            }

            work = newWork;
        }

        final List<int[]> result = new ArrayList<>();
        for (List<Integer> codec : work) {
            final int[] codecArray = new int[codec.size()];
            for (int i = 0; i < codec.size(); i++) {
                codecArray[i] = codec.get(i);
            }

            result.add(codecArray);
        }

        return result;
    }

    public static Writer<short[]> columnarWriteShort(int[] codec) {
        return (short[] xs, OutputStream os) -> {
            int pos = 0;
            for (int n : codec) {
                for (int x : xs) {
                    for (int i = 0; i < n; i++) {
                        os.write((x >>> ((pos + i) * 8)) & 0xFF);
                    }
                }
                pos += Math.max(0, n);
            }
        };
    }

    public static Writer<int[]> columnarWriteInt(int[] codec) {
        return (int[] xs, OutputStream os) -> {
            int pos = 0;
            for (int n : codec) {
                for (int x : xs) {
                    for (int i = 0; i < n; i++) {
                        os.write((x >>> ((pos + i) * 8)) & 0xFF);
                    }
                }
                pos += Math.max(0, n);
            }
        };
    }

    public static Writer<long[]> columnarWriteLong(int[] codec) {
        return (long[] xs, OutputStream os) -> {
            int pos = 0;
            for (int n : codec) {
                for (long x : xs) {
                    for (int i = 0; i < n; i++) {
                        os.write((int)((x >>> ((pos + i) * 8)) & 0xFF));
                    }
                }
                pos += Math.max(0, n);
            }
        };
    }

    public static Reader<short[]> columnarReadShort(int[] codec) {
        return (short[] xs, InputStream is) -> {
            int pos = 0;
            for (int n : codec) {
                for (int j = 0; j < xs.length; j++) {
                    for (int i = 0; i < n; i++) {
                        xs[j] |= is.read() << ((pos + i) * 8);
                    }
                }
                pos += Math.max(0, n);
            }
        };
    }

    public static Reader<int[]> columnarReadInt(int[] codec) {
        return (int[] xs, InputStream is) -> {
            int pos = 0;
            for (int n : codec) {
                for (int j = 0; j < xs.length; j++) {
                    for (int i = 0; i < n; i++) {
                        xs[j] |= is.read() << ((pos + i) * 8);
                    }
                }
                pos += Math.max(0, n);
            }
        };
    }

    public static Reader<long[]> columnarReadLong(int[] codec) {
        return (long[] xs, InputStream is) -> {
            int pos = 0;
            for (int n : codec) {
                for (int j = 0; j < xs.length; j++) {
                    for (int i = 0; i < n; i++) {
                        xs[j] |= (long)is.read() << ((pos + i) * 8);
                    }
                }
                pos += Math.max(0, n);
            }
        };
    }

    public static void writeFloat(float[] xs, OutputStream os) throws IOException {
        // For the tests. FIXME: use better params
        conditionFloat(writeFloatExponentsLiteral(), writeFloatMantissasLiteral(new int[] { 1, 1, 1 })).write(xs, os);
    }

    public static Writer<float[]> conditionFloat(Writer<byte[]> writeExponents,
                                                 Writer<int[]> writeMantissas) {
        return (float[] xs, OutputStream os) -> {
            // 1. Write descriptors (0, NaN or else positive/negative flag)
            final int defined;
            {
                final int[] definedByRef = new int[] { 0 };
                int i;
                for (i = 0; i < (xs.length >>> 2) << 2; i += 4) {
                    os.write((descriptor(definedByRef, xs[i + 0]) << 6) |
                             (descriptor(definedByRef, xs[i + 1]) << 4) |
                             (descriptor(definedByRef, xs[i + 2]) << 2) |
                             (descriptor(definedByRef, xs[i + 3]) << 0));
                }

                if (i < xs.length) {
                    int acc = 0;
                    for (; i < xs.length; i++) {
                        acc = (acc << 2) | descriptor(definedByRef, xs[i]);
                    }
                    os.write(acc);
                }

                defined = definedByRef[0];
            }

            // 2. Gather bits
            int j = 0;
            final byte[] exponents = new byte[defined];
            final int[] mantissas = new int[defined];
            for (final float x : xs) {
                if (x != 0.0 && !Float.isNaN(x)) {
                    final int bits = Float.floatToRawIntBits(x);
                    exponents[j] = (byte)((bits & 0x7F800000) >>> 23);
                    mantissas[j] = bits & 0x007FFFFF;
                    j++;
                }
            }

            // 3. Write
            writeExponents.write(exponents, os);
            writeMantissas.write(mantissas, os);
        };
    }

    public static void writeDouble(double[] xs, OutputStream os) throws IOException {
        // FIXME: better params
        conditionDouble(writeDoubleExponentsLiteral(new int[]{1, 1}), writeDoubleMantissasDelta(new int[]{1, 1, 1, 1, 1, 1, 1})).write(xs, os);
    }

    public static Writer<double[]> conditionDouble(Writer<short[]> writeExponents,
                                                   Writer<long[]> writeMantissas) {
        return (double[] xs, OutputStream os) -> {
            // 1. Write descriptors (0, NaN or else positive/negative flag)
            final int defined;
            {
                final int[] definedByRef = new int[] { 0 };
                int i;
                for (i = 0; i < (xs.length >>> 2) << 2; i += 4) {
                    os.write((descriptor(definedByRef, xs[i + 0]) << 6) |
                             (descriptor(definedByRef, xs[i + 1]) << 4) |
                             (descriptor(definedByRef, xs[i + 2]) << 2) |
                             (descriptor(definedByRef, xs[i + 3]) << 0));
                }

                if (i < xs.length) {
                    int acc = 0;
                    for (; i < xs.length; i++) {
                        acc = (acc << 2) | descriptor(definedByRef, xs[i]);
                    }
                    os.write(acc);
                }

                defined = definedByRef[0];
            }

            // 2. Gather bits. FIXME: try version with exponent and mantissa packed together
            int j = 0;
            final short[] exponents = new short[defined];
            final long[] mantissas = new long[defined];
            for (int i = 0; i < xs.length; i++) {
                final double x = xs[i];
                if (x != 0.0 && !Double.isNaN(x)) {
                    final long bits = Double.doubleToRawLongBits(x) & 0x7FFFFFFFFFFFFFFFL;
                    exponents[j] = (short)((bits >>> 52) & 0x7FFL);
                    mantissas[j] = bits & 0x000FFFFFFFFFFFFFL;
                    j++;
                }
            }

            // 3. Write
            writeExponents.write(exponents, os);
            writeMantissas.write(mantissas, os);
        };
    }

    private static int undescriptor(float[] xs, int i, int descriptor) {
        switch (descriptor & 0x3) {
            case 0: return 0;
            case 1: xs[i] = Float.NaN; return 0;
            default: xs[i] = (descriptor & 0x1) == 0 ? 1f : -1f; return 1;
        }
    }

    private static int undescriptor(double[] xs, int i, int descriptor) {
        switch (descriptor & 0x3) {
            case 0: return 0;
            case 1: xs[i] = Double.NaN; return 0;
            default: xs[i] = (descriptor & 0x1) == 0 ? 1.0 : -1.0; return 1;
        }
    }

    public static void readFloat(float[] xs, InputStream is) throws IOException {
        // FIXME: better params
        unconditionFloat(readFloatExponentsLiteral(), readFloatMantissasLiteral(new int[] { 1, 1, 1 })).read(xs, is);
    }

    public static Reader<float[]> unconditionFloat(Reader<byte[]> readExponents, Reader<int[]> readMantissas) {
        return (float[] xs, InputStream is) -> {
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

            // 2. Gather bits
            final byte[] exponents = new byte[defined];
            readExponents.read(exponents, is);
            final int[] mantissas = new int[defined];
            readMantissas.read(mantissas, is);

            // 3. Reassemble
            int j = 0;
            for (int i = 0; i < xs.length; i++) {
                final float x = xs[i];
                if (x != 0.0 && !Float.isNaN(x)) {
                    final byte exponent = exponents[j];
                    final int mantissa = mantissas[j];
                    j++;
                    xs[i] = Float.intBitsToFloat((x < 0 ? 0x80000000 : 0x00000000) | (((int)exponent & 0xFF) << 23) | mantissa);
                }
            }
        };
    }

    public static void readDouble(double[] xs, InputStream is) throws IOException {
        // FIXME: better params
        unconditionDouble(readDoubleExponentsLiteral(new int[]{1, 1}), readDoubleMantissasDelta(new int[]{1, 1, 1, 1, 1, 1, 1})).read(xs, is);
    }

    public static Reader<double[]> unconditionDouble(Reader<short[]> readExponents, Reader<long[]> readMantissas) {
        return (double[] xs, InputStream is) -> {
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

            // 2. Gather bits
            final short[] exponents = new short[defined];
            readExponents.read(exponents, is);
            final long[] mantissas = new long[defined];
            readMantissas.read(mantissas, is);

            // 3. Reassemble
            int j = 0;
            for (int i = 0; i < xs.length; i++) {
                final double x = xs[i];
                if (x != 0.0 && !Double.isNaN(x)) {
                    final short exponent = exponents[j];
                    final long mantissa = mantissas[j];
                    j++;
                    xs[i] = Double.longBitsToDouble((x < 0 ? 0x8000000000000000L : 0x0000000000000000L) |
                            ((long)exponent << 52) | mantissa);
                }
            }
        };
    }
}
