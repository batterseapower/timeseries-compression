package uk.co.omegaprime;

import org.iq80.snappy.SnappyInputStream;
import org.iq80.snappy.SnappyOutputStream;
import org.junit.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.*;

import static org.junit.Assert.assertArrayEquals;

public class ConditionerParameterSearchTest {
    private static class Pair<T> {
        public final String description;
        public final Conditioner.Writer<T> writer;
        public final Conditioner.Reader<T> reader;

        public Pair(String description, Conditioner.Writer<T> writer, Conditioner.Reader<T> reader) {
            this.description = description;
            this.writer = writer;
            this.reader = reader;
        }

        @Override
        public String toString() {
            return description;
        }
    }

    private static <T> List<Pair<T>> pairs(int length,
                                           Function<int[], Conditioner.Writer<T>> writerLiteral, Function<int[], Conditioner.Reader<T>> readerLiteral,
                                           Function<int[], Conditioner.Writer<T>> writerDelta,   Function<int[], Conditioner.Reader<T>> readerDelta) {
        final List<Pair<T>> result = new ArrayList<>();
        for (int[] codec : Conditioner.validCodecs(length)) {
            result.add(new Pair<T>("Literal " + Arrays.toString(codec), writerLiteral.apply(codec), readerLiteral.apply(codec)));
            result.add(new Pair<T>("Delta "   + Arrays.toString(codec), writerDelta.apply(codec),   readerDelta.apply(codec)));
        }
        return result;
    }

    private interface IOFunction<A, B> {
        public B apply(A a) throws IOException;
    }

    private static float[] toFloatArray(List<Float> floats) {
        final float[] result = new float[floats.size()];
        for (int i = 0; i < floats.size(); i++) {
            result[i] = floats.get(i);
        }

        return result;
    }

    private static List<float[]> loadFullData() throws IOException {
        final List<float[]> result = new ArrayList<>();

        final File root = new File("/Users/mbolingbroke/Programming/yahoo-sample-data");
        for (File file : root.listFiles()) {
            if (file.getName().endsWith(".csv")) {
                final List<Float> floats = new ArrayList<>();
                try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        final String[] parts = line.split(",");
                        if (parts.length == 1) continue;
                        floats.add(Float.parseFloat(parts[1]));
                    }
                }

                result.add(toFloatArray(floats));
            }
        }

        return result;
    }

    @Test
    public void benchmark() throws IOException {
        /*
        Snappy compress: 0.156672s
        Compressed to 10876881 bytes
        Uncompresses to 12908620 bytes
        Snappy decompress: 0.162463s

        Deflate Fastest compress: 6.017877s
        Compressed to 7770401 bytes
        Uncompresses to 12908620 bytes
        Deflate Fastest decompress: 4.260146s

        Deflate Slowest compress: 7.06043s
        Compressed to 7492001 bytes
        Uncompresses to 12908620 bytes
        Deflate Slowest decompress: 3.968221s

        Deflate Normal compress: 5.201332s
        Compressed to 7515522 bytes
        Uncompresses to 12908620 bytes
        Deflate Normal decompress: 4.056792s
         */
        benchmark("Snappy",          SnappyOutputStream::new,                                                        SnappyInputStream::new);
        benchmark("Deflate Fastest", os -> new DeflaterOutputStream(os, new Deflater(Deflater.BEST_SPEED)),          InflaterInputStream::new);
        benchmark("Deflate Slowest", os -> new DeflaterOutputStream(os, new Deflater(Deflater.BEST_COMPRESSION)),    InflaterInputStream::new);
        benchmark("Deflate Normal",  os -> new DeflaterOutputStream(os, new Deflater(Deflater.DEFAULT_COMPRESSION)), InflaterInputStream::new);
    }

    private static class Stopwatch implements AutoCloseable {
        private final String name;
        private final long start;

        public Stopwatch(String name) {
            this.name = name;
            this.start = System.nanoTime();
        }

        @Override
        public void close() {
            final long end = System.nanoTime();
            System.out.println(name + ": " + (end - start) / 1e9 + "s");
        }
    }

    public static void benchmark(String compressor, IOFunction<OutputStream, OutputStream> mkCompressor, IOFunction<InputStream, InputStream> mkUncompressor) throws IOException {
        final List<float[]> data = loadFullData();

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int wrote = 0;
        try (Stopwatch _ = new Stopwatch(compressor + " compress")) {
            try (DataOutputStream daos = new DataOutputStream(mkCompressor.apply(baos))) {
                daos.writeInt(data.size());
                for (float[] floats : data) {
                    daos.writeInt(floats.length);
                    for (float x : floats) {
                        wrote++;
                        daos.writeFloat(x);
                    }
                }
            }
        }

        System.out.println("Compressed to " + baos.size() + " bytes");
        System.out.println("Uncompresses to " + skipAll(mkUncompressor.apply(new ByteArrayInputStream(baos.toByteArray()))) + " bytes");

        int read = 0;
        try (Stopwatch _ = new Stopwatch(compressor + " decompress")) {
            try (DataInputStream dis = new DataInputStream(mkUncompressor.apply(new ByteArrayInputStream(baos.toByteArray())))) {
                final int length0 = dis.readInt();
                for (int i = 0; i < length0; i++) {
                    final int length1 = dis.readInt();
                    for (int j = 0; j < length1; j++) {
                        read++;
                        try {
                            dis.readFloat();
                        } catch (EOFException e) {
                            throw new IOException(read + " of " + wrote, e);
                        }
                    }
                }
            }
        }

        if (read != wrote) {
            throw new IllegalStateException(read + " != " + wrote);
        }

        System.out.println();
    }

    private static long skipAll(InputStream is) throws IOException {
        long skipped;
        long totalSkipped = 0;
        do {
            skipped = is.skip(1024 * 1024);
            if (skipped == 0) {
                if (is.read() >= 0) {
                    skipped = 1;
                }
            }
            totalSkipped += skipped;
        } while (skipped > 0);

        return totalSkipped;
    }

    @Test
    public void allFloatParameterCombinationsWork() throws IOException {
        allFloatParameterCombinationsWork("Snappy", SnappyOutputStream::new, SnappyInputStream::new);
        allFloatParameterCombinationsWork("Gzip",   GZIPOutputStream::new,   GZIPInputStream::new);
    }

    public void allFloatParameterCombinationsWork(String compressor, IOFunction<OutputStream, OutputStream> mkCompressor, IOFunction<InputStream, InputStream> mkUncompressor) throws IOException {
        //final List<float[]> inputs = Arrays.asList(Utils.getExampleData());
        final List<float[]> inputs = loadFullData();

        {
            long size = 0;
            for (float[] input : inputs) {
                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (final DataOutputStream dos = new DataOutputStream(mkCompressor.apply(baos))) {
                    for (float x : input) {
                        dos.writeFloat(x);
                    }
                }
                size += baos.size();
            }
            System.err.println(compressor + " baseline: " + size);
        }

        for (Pair<byte[]> exponent : Arrays.<Pair<byte[]>>asList(new Pair<byte[]>("Literal", Conditioner::writeFloatExponentsLiteral, Conditioner::readFloatExponentsLiteral), new Pair<byte[]>("Delta", Conditioner::writeFloatExponentsDelta, Conditioner::readFloatExponentsDelta))) {
            for (Pair<int[]> mantissa : ConditionerParameterSearchTest.<int[]>pairs(3, Conditioner::writeFloatMantissasLiteral, Conditioner::readFloatMantissasLiteral, Conditioner::writeFloatMantissasDelta, Conditioner::readFloatMantissasDelta)) {
                final String method = String.format("%s\t%s\t%s", compressor, exponent, mantissa);

                long size = 0;
                for (float[] input : inputs) {
                    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    try (final OutputStream os = mkCompressor.apply(baos)) {
                        Conditioner.condition(exponent.writer, mantissa.writer, input, os);
                    }

                    final float[] output = new float[input.length];
                    final InputStream is = mkUncompressor.apply(new ByteArrayInputStream(baos.toByteArray()));
                    Conditioner.uncondition(exponent.reader, mantissa.reader, output, is);

                    assertArrayEquals(method, input, output, 0f);

                    size += baos.size();
                }

                System.out.println(method + "\t" + size);
            }
        }
    }

    @Test
    public void allDoubleParameterCombinationsWork() throws IOException {
        allDoubleParameterCombinationsWork("Snappy", SnappyOutputStream::new, SnappyInputStream::new);
        allDoubleParameterCombinationsWork("Gzip",   GZIPOutputStream::new,   GZIPInputStream::new);
    }

    public void allDoubleParameterCombinationsWork(String compressor, IOFunction<OutputStream, OutputStream> mkCompressor, IOFunction<InputStream, InputStream> mkUncompressor) throws IOException {
        //final List<double[]> inputs = Arrays.asList(Utils.floatsToDoubles(Utils.getExampleData()));
        final List<double[]> inputs = loadFullData().stream().map(Utils::floatsToDoubles).collect(Collectors.toList());

        {
            long size = 0;
            for (double[] input : inputs) {
                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (final DataOutputStream dos = new DataOutputStream(mkCompressor.apply(baos))) {
                    for (double x : input) {
                        dos.writeDouble(x);
                    }
                }
                size += baos.size();
            }
            System.err.println(compressor + " baseline: " + size);
        }

        for (Pair<short[]> exponent : ConditionerParameterSearchTest.<short[]>pairs(2, Conditioner::writeDoubleExponentsLiteral, Conditioner::readDoubleExponentsLiteral, Conditioner::writeDoubleExponentsDelta, Conditioner::readDoubleExponentsDelta)) {
            for (Pair<long[]> mantissa : ConditionerParameterSearchTest.<long[]>pairs(7, Conditioner::writeDoubleMantissasLiteral, Conditioner::readDoubleMantissasLiteral, Conditioner::writeDoubleMantissasDelta, Conditioner::readDoubleMantissasDelta)) {
                final String method = String.format("%s\t%s\t%s", compressor, exponent, mantissa);

                long size = 0;
                for (double[] input : inputs) {
                    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    try (final OutputStream os = mkCompressor.apply(baos)) {
                        Conditioner.condition(exponent.writer, mantissa.writer, input, os);
                    }

                    final double[] output = new double[input.length];
                    final InputStream is = mkUncompressor.apply(new ByteArrayInputStream(baos.toByteArray()));
                    Conditioner.uncondition(exponent.reader, mantissa.reader, output, is);

                    assertArrayEquals(method, input, output, 0.0);

                    size += baos.size();
                }

                System.out.println(method + "\t" + size);
            }
        }
    }
}
