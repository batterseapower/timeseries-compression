package uk.co.omegaprime;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;
import org.iq80.snappy.SnappyInputStream;
import org.iq80.snappy.SnappyOutputStream;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.tukaani.xz.LZMA2Options;

import java.io.*;
import java.util.List;
import java.util.zip.*;

public class JMHCompressorTest {
    private final static List<float[]> data;

    static {
        try {
            data = ConditionerParameterSearchTest.loadFullFloatData();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Benchmark
    public int none() throws IOException {
        return benchmark(x -> x, x -> x);
    }
    @Benchmark
    public int snappy() throws IOException {
        return benchmark(SnappyOutputStream::new,                                                        SnappyInputStream::new);
    }
    @Benchmark
    public int gzip() throws IOException {
        return benchmark(GZIPOutputStream::new,                                                          GZIPInputStream::new);
    }
    @Benchmark
    public int deflateFastest() throws IOException {
        return benchmark(os -> new DeflaterOutputStream(os, new Deflater(Deflater.BEST_SPEED)),          InflaterInputStream::new);
    }
    @Benchmark
    public int deflateNormal() throws IOException {
        return benchmark(os -> new DeflaterOutputStream(os, new Deflater(Deflater.DEFAULT_COMPRESSION)), InflaterInputStream::new);
    }
    @Benchmark
    public int deflateSlowest() throws IOException {
        return benchmark(os -> new DeflaterOutputStream(os, new Deflater(Deflater.BEST_COMPRESSION)),    InflaterInputStream::new);
    }
    @Benchmark
    public int bzipFastest() throws IOException {
        return benchmark(os -> new BZip2CompressorOutputStream(os, BZip2CompressorOutputStream.MIN_BLOCKSIZE), BZip2CompressorInputStream::new);
    }
    @Benchmark
    public int bzipSlowest() throws IOException {
        return benchmark(os -> new BZip2CompressorOutputStream(os, BZip2CompressorOutputStream.MAX_BLOCKSIZE), BZip2CompressorInputStream::new);
    }
    @Benchmark
    public int xzFastest() throws IOException {
        return benchmark(os -> new XZCompressorOutputStream(os, LZMA2Options.PRESET_MIN),                      XZCompressorInputStream::new);
    }
    @Benchmark
    public int xzNormal() throws IOException {
        return benchmark(os -> new XZCompressorOutputStream(os, LZMA2Options.PRESET_DEFAULT),                  XZCompressorInputStream::new);
    }
    @Benchmark
    public int xzSlowest() throws IOException {
        return benchmark(os -> new XZCompressorOutputStream(os, LZMA2Options.PRESET_MAX),                      XZCompressorInputStream::new);
    }

    private interface IOFunction<A, B> {
        public B apply(A a) throws IOException;
    }

    public static int benchmark(IOFunction<OutputStream, OutputStream> mkCompressor, IOFunction<InputStream, InputStream> mkUncompressor) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int wrote = 0;
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

        final long uncompressedSize = Utils.skipAll(mkUncompressor.apply(new ByteArrayInputStream(baos.toByteArray())));
        /*System.out.println("Compressed to " + baos.size() + " bytes");
        System.out.println("Uncompresses to " + uncompressedSize + " bytes");
        System.out.println(String.format("Ratio %.3f", (double)baos.size() / uncompressedSize));*/

        int read = 0;
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

        if (read != wrote) {
            throw new IllegalStateException(read + " != " + wrote);
        }

        return (int)(read + uncompressedSize);
    }

    /*
    Benchmark                          Mode  Cnt  Score   Error  Units
    JMHCompressorTest.bzipFastest     thrpt   40  0.323 ± 0.005  ops/s
    JMHCompressorTest.bzipSlowest     thrpt   40  0.267 ± 0.022  ops/s
    JMHCompressorTest.deflateFastest  thrpt   40  0.117 ± 0.002  ops/s
    JMHCompressorTest.deflateNormal   thrpt   40  0.107 ± 0.001  ops/s
    JMHCompressorTest.deflateSlowest  thrpt   40  0.088 ± 0.001  ops/s
    JMHCompressorTest.gzip            thrpt   40  0.105 ± 0.001  ops/s
    JMHCompressorTest.none            thrpt   40  7.866 ± 0.154  ops/s
    JMHCompressorTest.snappy          thrpt   40  4.656 ± 0.066  ops/s
    JMHCompressorTest.xzFastest       thrpt   40  0.245 ± 0.007  ops/s
    JMHCompressorTest.xzNormal        thrpt   40  0.090 ± 0.001  ops/s
    JMHCompressorTest.xzSlowest       thrpt   40  0.087 ± 0.003  ops/s
     */
    public static void main(String[] args) throws IOException, RunnerException {
        new Runner(new OptionsBuilder().forks(4).measurementIterations(10).build()).run();
    }
}
