package uk.co.omegaprime;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;
import org.iq80.snappy.SnappyInputStream;
import org.iq80.snappy.SnappyOutputStream;
import org.junit.Test;
import org.tukaani.xz.LZMA2Options;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.*;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assume.assumeTrue;

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

    static List<float[]> loadFullFloatData() throws IOException {
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

        System.out.println(result.stream().mapToInt(xs -> xs.length).sum());
        return result;
    }

    private List<double[]> loadFullDoubleData() throws IOException {
        return loadFullFloatData().stream().map(Utils::floatsToDoubles).collect(Collectors.toList());
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
        benchmark("None",            x -> x,                                                                         x -> x);
        benchmark("None",            x -> x,                                                                         x -> x);
        benchmark("Snappy",          SnappyOutputStream::new,                                                        SnappyInputStream::new);
        benchmark("GZip",            GZIPOutputStream::new,                                                          GZIPInputStream::new);
        benchmark("Deflate Fastest", os -> new DeflaterOutputStream(os, new Deflater(Deflater.BEST_SPEED)),          InflaterInputStream::new);
        benchmark("Deflate Normal",  os -> new DeflaterOutputStream(os, new Deflater(Deflater.DEFAULT_COMPRESSION)), InflaterInputStream::new);
        benchmark("Deflate Slowest", os -> new DeflaterOutputStream(os, new Deflater(Deflater.BEST_COMPRESSION)),    InflaterInputStream::new);
        benchmark("BZip2 Fastest",   os -> new BZip2CompressorOutputStream(os, BZip2CompressorOutputStream.MIN_BLOCKSIZE), BZip2CompressorInputStream::new);
        benchmark("BZip2 Slowest",   os -> new BZip2CompressorOutputStream(os, BZip2CompressorOutputStream.MAX_BLOCKSIZE), BZip2CompressorInputStream::new);
        benchmark("XZ Fastest",      os -> new XZCompressorOutputStream(os, LZMA2Options.PRESET_MIN),                      XZCompressorInputStream::new);
        benchmark("XZ Normal",       os -> new XZCompressorOutputStream(os, LZMA2Options.PRESET_DEFAULT),                  XZCompressorInputStream::new);
        benchmark("XZ Slowest",      os -> new XZCompressorOutputStream(os, LZMA2Options.PRESET_MAX),                      XZCompressorInputStream::new);
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
        final List<float[]> data = loadFullFloatData();

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

        final long uncompressedSize = Utils.skipAll(mkUncompressor.apply(new ByteArrayInputStream(baos.toByteArray())));
        System.out.println("Compressed to " + baos.size() + " bytes");
        System.out.println("Uncompresses to " + uncompressedSize + " bytes");
        System.out.println(String.format("Ratio %.3f", (double)baos.size() / uncompressedSize));

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

    private BufferedWriter openWriter(String what) throws IOException {
        return new BufferedWriter(new FileWriter("/Users/mbolingbroke/Programming/timeseries-compression/" + what + ".tsv"));
    }

    @Test
    public void allFloatParameterCombinationsKnockout() throws IOException {
        final List<float[]> fullData = loadFullFloatData();

        try (BufferedWriter noSplitWriter = openWriter("floats-knockout-nosplit");
             BufferedWriter splitWriter = openWriter("floats-knockout")) {
            for (float knockout : new float[] { 0f, 0.1f, 0.5f, 0.75f }) {
                final Random r = new Random(1337);
                final List<float[]> check = fullData.stream().map(xs -> {
                    final float[] ys = xs.clone();
                    for (int i = 0; i < ys.length; i++) {
                        if (r.nextDouble() < knockout) {
                            ys[i] = Float.NaN;
                        }
                    }
                    return ys;
                }).collect(Collectors.toList());

                allFloatParameterCombinationsWork(check, "Snappy\t" + knockout, SnappyOutputStream::new, SnappyInputStream::new, noSplitWriter, splitWriter);
            }
        }
    }

    @Test
    public void allFloatParameterCombinationsWork() throws IOException {
        try (BufferedWriter noSplitWriter = openWriter("floats-nosplit");
             BufferedWriter splitWriter = openWriter("floats")) {
            allFloatParameterCombinationsWork(loadFullFloatData(), "Snappy", SnappyOutputStream::new,                                                              SnappyInputStream::new,          noSplitWriter, splitWriter);
            allFloatParameterCombinationsWork(loadFullFloatData(), "BZ2",    os -> new BZip2CompressorOutputStream(os, BZip2CompressorOutputStream.MIN_BLOCKSIZE), BZip2CompressorInputStream::new, noSplitWriter, splitWriter);
        }
    }

    public void allFloatParameterCombinationsWork(List<float[]> inputs, String compressor, IOFunction<OutputStream, OutputStream> mkCompressor, IOFunction<InputStream, InputStream> mkUncompressor,
                                                  BufferedWriter noSplitWriter, BufferedWriter splitWriter) throws IOException {
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


        for (Pair<float[]> bits : ConditionerParameterSearchTest.<float[]>pairs(4, Conditioner::writeFloatLiteral, Conditioner::readFloatLiteral, Conditioner::writeFloatDelta, Conditioner::readFloatDelta)) {
            final String method = String.format("%s\t%s", compressor, bits);

            noSplitWriter.write(method);
            noSplitWriter.write('\t');
            noSplitWriter.write(Long.toString(evaluateFloatPair(method, inputs, mkCompressor, mkUncompressor,
                                                                bits.writer, bits.reader)));
            noSplitWriter.write('\n');
        }

        for (boolean specialCases : new boolean[] { false, true }) {
            final Conditioner conditioner = new Conditioner(specialCases);
            for (Pair<byte[]> exponent : Arrays.<Pair<byte[]>>asList(new Pair<byte[]>("Literal", Conditioner.writeFloatExponentsLiteral(), Conditioner.readFloatExponentsLiteral()), new Pair<byte[]>("Delta", Conditioner.writeFloatExponentsDelta(), Conditioner.readFloatExponentsDelta()))) {
                for (Pair<int[]> mantissa : ConditionerParameterSearchTest.<int[]>pairs(3, Conditioner::writeFloatMantissasLiteral, Conditioner::readFloatMantissasLiteral, Conditioner::writeFloatMantissasDelta, Conditioner::readFloatMantissasDelta)) {
                    final String method = String.format("%s\t%s\t%s\t%s", specialCases, compressor, exponent, mantissa);

                    splitWriter.write(method);
                    splitWriter.write('\t');
                    splitWriter.write(Long.toString(evaluateFloatPair(method, inputs, mkCompressor, mkUncompressor,
                                                                      conditioner.conditionFloat  (exponent.writer, mantissa.writer),
                                                                      conditioner.unconditionFloat(exponent.reader, mantissa.reader))));
                    splitWriter.write('\n');
                }
            }
        }
    }

    private static long evaluateFloatPair(String method, List<float[]> inputs,
                                          IOFunction<OutputStream, OutputStream> mkCompressor, IOFunction<InputStream, InputStream> mkUncompressor,
                                          Conditioner.Writer<float[]> writer, Conditioner.Reader<float[]> reader) throws IOException {
        long size = 0;
        for (float[] input : inputs) {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (final OutputStream os = mkCompressor.apply(baos)) {
                writer.write(input, os);
            }

            final float[] output = new float[input.length];
            final InputStream is = mkUncompressor.apply(new ByteArrayInputStream(baos.toByteArray()));
            reader.read(output, is);

            assertArrayEquals(method, input, output, 0f);

            size += baos.size();
        }

        return size;
    }

    @Test
    public void allDoubleParameterCombinationsKnockout() throws IOException {
        final List<double[]> fullData = loadFullDoubleData();

        try (BufferedWriter noSplitWriter = openWriter("doubles-knockout-nosplit");
             BufferedWriter splitWriter = openWriter("doubles-knockout")) {
            for (float knockout : new float[] { 0f, 0.1f, 0.5f, 0.75f }) {
                final Random r = new Random(1337);
                final List<double[]> check = fullData.stream().map(xs -> {
                    final double[] ys = xs.clone();
                    for (int i = 0; i < ys.length; i++) {
                        if (r.nextDouble() < knockout) {
                            ys[i] = Double.NaN;
                        }
                    }
                    return ys;
                }).collect(Collectors.toList());

                allDoubleParameterCombinationsWork(check, "Snappy\t" + knockout, SnappyOutputStream::new, SnappyInputStream::new, noSplitWriter, splitWriter);
            }
        }
    }

    @Test
    public void allDoubleParameterCombinationsWork() throws IOException {
        try (BufferedWriter noSplitWriter = openWriter("doubles-nosplit");
             BufferedWriter splitWriter = openWriter("doubles")) {
            allDoubleParameterCombinationsWork(loadFullDoubleData(), "Snappy", SnappyOutputStream::new,                                                              SnappyInputStream::new,          noSplitWriter, splitWriter);
            allDoubleParameterCombinationsWork(loadFullDoubleData(), "BZ2",    os -> new BZip2CompressorOutputStream(os, BZip2CompressorOutputStream.MIN_BLOCKSIZE), BZip2CompressorInputStream::new, noSplitWriter, splitWriter);
        }
    }

    public void allDoubleParameterCombinationsWork(List<double[]> inputs, String compressor, IOFunction<OutputStream, OutputStream> mkCompressor, IOFunction<InputStream, InputStream> mkUncompressor,
                                                   BufferedWriter noSplitWriter, BufferedWriter splitWriter) throws IOException {
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


        for (Pair<double[]> bits : ConditionerParameterSearchTest.<double[]>pairs(8, Conditioner::writeDoubleLiteral, Conditioner::readDoubleLiteral, Conditioner::writeDoubleDelta, Conditioner::readDoubleDelta)) {
            final String method = String.format("%s\t%s", compressor, bits);

            noSplitWriter.write(method);
            noSplitWriter.write('\t');
            noSplitWriter.write(Long.toString(evaluateDoublePair(method, inputs, mkCompressor, mkUncompressor,
                                                                 bits.writer, bits.reader)));
            noSplitWriter.write('\n');
        }

        for (boolean specialCases : new boolean[] { false, true }) {
            final Conditioner conditioner = new Conditioner(specialCases);
            for (Pair<short[]> exponent : ConditionerParameterSearchTest.<short[]>pairs(2, Conditioner::writeDoubleExponentsLiteral, Conditioner::readDoubleExponentsLiteral, Conditioner::writeDoubleExponentsDelta, Conditioner::readDoubleExponentsDelta)) {
                for (Pair<long[]> mantissa : ConditionerParameterSearchTest.<long[]>pairs(7, Conditioner::writeDoubleMantissasLiteral, Conditioner::readDoubleMantissasLiteral, Conditioner::writeDoubleMantissasDelta, Conditioner::readDoubleMantissasDelta)) {
                    final String method = String.format("%s\t%s\t%s\t%s", specialCases, compressor, exponent, mantissa);

                    splitWriter.write(method);
                    splitWriter.write('\t');
                    splitWriter.write(Long.toString(evaluateDoublePair(method, inputs, mkCompressor, mkUncompressor,
                                                                       conditioner.conditionDouble  (exponent.writer, mantissa.writer),
                                                                       conditioner.unconditionDouble(exponent.reader, mantissa.reader))));
                    splitWriter.write('\n');
                }
            }
        }
    }

    private static long evaluateDoublePair(String method, List<double[]> inputs,
                                           IOFunction<OutputStream, OutputStream> mkCompressor, IOFunction<InputStream, InputStream> mkUncompressor,
                                           Conditioner.Writer<double[]> writer, Conditioner.Reader<double[]> reader) throws IOException {
        long size = 0;
        for (double[] input : inputs) {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (final OutputStream os = mkCompressor.apply(baos)) {
                writer.write(input, os);
            }

            final double[] output = new double[input.length];
            final InputStream is = mkUncompressor.apply(new ByteArrayInputStream(baos.toByteArray()));
            reader.read(output, is);

            assertArrayEquals(method, input, output, 0d);

            size += baos.size();
        }

        return size;
    }
}
