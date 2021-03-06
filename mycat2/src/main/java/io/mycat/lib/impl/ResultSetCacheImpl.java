//package io.mycat.lib.impl;
//
//import io.mycat.RootHelper;
//import io.mycat.ScheduleUtil;
//import io.mycat.beans.resultset.MycatResultSetResponse;
//import io.mycat.beans.resultset.MycatResultSetType;
//import lombok.SneakyThrows;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.io.File;
//import java.io.IOException;
//import java.nio.ByteBuffer;
//import java.nio.MappedByteBuffer;
//import java.nio.channels.FileChannel;
//import java.nio.file.Files;
//import java.nio.file.Paths;
//import java.nio.file.StandardOpenOption;
//import java.util.Iterator;
//import java.util.concurrent.TimeUnit;
//
///**
// * @author chen junwen
// */
//
//public class ResultSetCacheImpl implements ResultSetCacheRecorder {
//    private static final Logger LOGGER = LoggerFactory.getLogger(ResultSetCacheImpl.class);
//    FileChannel channel;
//    MappedByteBuffer buffer;
//
//    private final File flie;
//    private final ByteBuffer tmp = ByteBuffer.allocate(4);
//
//    private int startPosition;
//    private int rowStartPosition;
//    private boolean deleteOnExit;
//
//    @SneakyThrows
//    public ResultSetCacheImpl(String simpleName) throws IOException {
//        this(Files.createTempFile(Paths.get(getTmpDir()), "resultSetCache", simpleName).toString(), true);
//    }
//
//    private static String getTmpDir() {
//        String tempDirectory = "";
//        try {
//            tempDirectory = RootHelper.INSTANCE.getConfigProvider().currentConfig().getServer().getTempDirectory();
//        } catch (Exception e) {
//            LOGGER.warn("",e);
//        }
//        return tempDirectory;
//    }
//
//    public ResultSetCacheImpl(String file, boolean deleteOnExit) {
//        this.deleteOnExit = deleteOnExit;
//        try {
//            this.flie = getFile(file);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    private static File getFile(String flie) throws IOException {
//        File file = new File(flie);
//        if (!file.exists()) file.createNewFile();
//        return file;
//    }
//
//    @Override
//    public void open() throws IOException {
//        if (channel == null || !channel.isOpen()) {
//            channel = FileChannel.open(flie.toPath(), StandardOpenOption.WRITE, StandardOpenOption.READ);
//        } else {
//            channel.position(0);
//        }
//
//    }
//
//    @Override
//    public void sync() throws IOException {
//        if (channel != null) {
//            channel.force(false);
//        }
//
//    }
//
//    @Override
//    public void close() throws IOException {
//        if (channel != null) {
//            channel.close();
//        }
//        if (deleteOnExit) {
//            if (flie.exists()) {
//            ScheduleUtil.getTimer().scheduleWithFixedDelay(() -> {
//                try {
//                    flie.delete();
//                }catch (Exception e){
//                    LOGGER.error("",e);
//                }
//            },30,30, TimeUnit.SECONDS);
//            }
//        }
//    }
//
//    @Override
//    public void startRecordColumn(int columnCount) {
//        try {
//            startPosition = (int) channel.position();
//            tmp.putInt(0, columnCount);
//            tmp.position(0);
//            channel.write(tmp);
//        } catch (IOException e) {
//            LOGGER.error("{}", e);
//        }
//
//    }
//
//    @Override
//    public void addColumnDefBytes(byte[] bytes) {
//        try {
//            write(bytes);
//        } catch (IOException e) {
//            LOGGER.error("{}", e);
//        }
//    }
//
//    private void write(byte[] bytes) throws IOException {
//        tmp.putInt(0, bytes.length);
//        tmp.position(0);
//        channel.write(new ByteBuffer[]{tmp, ByteBuffer.wrap(bytes)});
//    }
//
//    @Override
//    public void startRecordRow() {
//        try {
//            this.rowStartPosition = (int) channel.position();
//        } catch (IOException e) {
//            LOGGER.error("{}", e);
//        }
//    }
//
//    @Override
//    public void addRowBytes(byte[] bytes) {
//        try {
//            write(bytes);
//        } catch (IOException e) {
//            LOGGER.error("{}", e);
//        }
//    }
//
//    @Override
//    public Token endRecord() {
//        try {
//            return new TokenImpl(this.startPosition, this.rowStartPosition, (int) channel.position());
//        } catch (IOException e) {
//            LOGGER.error("{}", e);
//            return null;
//        }
//    }
//
//    @Override
//    public MycatResultSetResponse newMycatResultSetResponse(Token token) throws IOException {
//        TokenImpl t = (TokenImpl) token;
//        return newMycatResultSetResponse(t.startPosition, t.rowStartPosition, t.endPosition);
//    }
//
//
//    public MycatResultSetResponse newMycatResultSetResponse(int startOffset, int rowStartOffset, int endOffset) throws IOException {
//        if (buffer == null || !buffer.isLoaded()) {
//            open();
//            buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, channel.size());
//        }
//        return new MycatResultSetResponse() {
//            final ByteBuffer buffer = ResultSetCacheImpl.this.buffer.asReadOnlyBuffer();
//            final int count = (int) buffer.getInt(startOffset);
//
//            @Override
//            public int columnCount() {
//                return count;
//            }
//
//            @Override
//            public Iterator<ByteBuffer> columnDefIterator() {
//                return new Iterator<ByteBuffer>() {
//                    int index = 0;
//                    int position = startOffset + 4;
//
//                    @Override
//                    public boolean hasNext() {
//                        buffer.clear();
//                        return index < count;
//                    }
//
//                    @Override
//                    public ByteBuffer next() {
//                        index++;
//                        int length = buffer.getInt(position);
//                        int startIndex = position + 4;
//                        buffer.position(startIndex);
//                        position = startIndex + length;
//                        buffer.limit(position);
//                        return buffer.slice();
//                    }
//                };
//            }
//
//            @Override
//            public Iterator<ByteBuffer> rowIterator() {
//                return new Iterator<ByteBuffer>() {
//                    int position = rowStartOffset;
//
//                    @Override
//                    public boolean hasNext() {
//                        buffer.clear();
//                        return position < endOffset;
//                    }
//
//                    @Override
//                    public ByteBuffer next() {
//                        int length = buffer.getInt(position);
//                        int startIndex = position + 4;
//                        buffer.position(startIndex);
//                        position = startIndex + length;
//                        buffer.limit(position);
//                        return buffer.slice();
//                    }
//                };
//            }
//
//            @Override
//            public void close() {
//
//            }
//        };
//    }
//
//    @Override
//    public void finalize() throws Throwable {
//        super.finalize();
//        close();
//    }
//
//    static class TokenImpl implements Token {
//        final int startPosition;
//        final int rowStartPosition;
//        final int endPosition;
//
//        public TokenImpl(int startPosition, int rowStartPosition, int endPosition) {
//            this.startPosition = startPosition;
//            this.rowStartPosition = rowStartPosition;
//            this.endPosition = endPosition;
//        }
//    }
//
//    public File getFlie() {
//        return flie;
//    }
//}