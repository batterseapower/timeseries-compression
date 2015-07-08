package uk.co.omegaprime;

import org.iq80.snappy.SnappyInputStream;
import org.iq80.snappy.SnappyOutputStream;
import org.junit.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

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
            System.out.println(compressor + " baseline: " + size);
        }

        for (Pair<byte[]> exponent : Arrays.<Pair<byte[]>>asList(new Pair<byte[]>("Literal", Conditioner::writeFloatExponentsLiteral, Conditioner::readFloatExponentsLiteral), new Pair<byte[]>("Delta", Conditioner::writeFloatExponentsDelta, Conditioner::readFloatExponentsDelta))) {
            for (Pair<int[]> mantissa : ConditionerParameterSearchTest.<int[]>pairs(3, Conditioner::writeFloatMantissasLiteral, Conditioner::readFloatMantissasLiteral, Conditioner::writeFloatMantissasDelta, Conditioner::readFloatMantissasDelta)) {
                final String method = String.format("%s / E %s / M %s", compressor, exponent, mantissa);

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

                System.out.println(method + ": " + size);
            }
        }
    }

    @Test
    public void allDoubleParameterCombinationsWork() throws IOException {
        allDoubleParameterCombinationsWork("Snappy", SnappyOutputStream::new, SnappyInputStream::new);
        allDoubleParameterCombinationsWork("Gzip",   GZIPOutputStream::new,   GZIPInputStream::new);
    }

    public void allDoubleParameterCombinationsWork(String compressor, IOFunction<OutputStream, OutputStream> mkCompressor, IOFunction<InputStream, InputStream> mkUncompressor) throws IOException {
        final List<double[]> inputs = Arrays.asList(Utils.floatsToDoubles(Utils.getExampleData()));
        //final List<double[]> inputs = loadFullData();

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
            System.out.println(compressor + " baseline: " + size);
        }

        for (Pair<short[]> exponent : ConditionerParameterSearchTest.<short[]>pairs(2, Conditioner::writeDoubleExponentsLiteral, Conditioner::readDoubleExponentsLiteral, Conditioner::writeDoubleExponentsDelta, Conditioner::readDoubleExponentsDelta)) {
            for (Pair<long[]> mantissa : ConditionerParameterSearchTest.<long[]>pairs(7, Conditioner::writeDoubleMantissasLiteral, Conditioner::readDoubleMantissasLiteral, Conditioner::writeDoubleMantissasDelta, Conditioner::readDoubleMantissasDelta)) {
                final String method = String.format("%s / E %s / M %s", compressor, exponent, mantissa);

                long size = 0;
                for (double[] input : inputs) {
                    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    try (final OutputStream os = mkCompressor.apply(baos)) {
                        Conditioner.condition(exponent.writer, mantissa.writer, input, os);
                    }

                    final double[] output = new double[input.length];
                    final InputStream is = mkUncompressor.apply(new ByteArrayInputStream(baos.toByteArray()));
                    Conditioner.uncondition(exponent.reader, mantissa.reader, output, is);

                    assertArrayEquals(input, output, 0.0);

                    size += baos.size();
                }

                System.out.println(method + ": " + size);
            }
        }
    }
}
