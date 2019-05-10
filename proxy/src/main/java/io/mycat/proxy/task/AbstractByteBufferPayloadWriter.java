/**
 * Copyright (C) <2019>  <chen junwen>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.mycat.proxy.task;

import io.mycat.proxy.MycatReactorThread;
import io.mycat.proxy.NIOHandler;
import io.mycat.proxy.buffer.BufferPool;
import io.mycat.proxy.packet.MySQLPacket;
import io.mycat.proxy.packet.PacketSplitter;
import io.mycat.proxy.session.AbstractMySQLClientSession;
import io.mycat.proxy.session.MySQLClientSession;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public abstract class AbstractByteBufferPayloadWriter<T extends ByteBuffer> implements NIOHandler<AbstractMySQLClientSession>, PacketSplitter {
    private ByteBuffer[] buffers;
    private int startIndex;
    private int writeIndex;
    private int length;
    BufferPool bufferPool;
    SocketChannel socketChannel;
    int reminsPacketLen;
    int totalSize;
    int currentPacketLen;
    int offset;
    private MySQLClientSession mysql;

    public void request(MySQLClientSession mysql, T buffer, int position, int length, AsynTaskCallBack<MySQLClientSession> callBack) {
        try {
            this.mysql = mysql;
            this.buffers = new ByteBuffer[2];
            this.buffers[1] = buffer;
            this.writeIndex = this.startIndex = position;
            this.length = length;
            mysql.setCallBack(callBack);
            this.setServerSocket(mysql.channel());
            MycatReactorThread thread = (MycatReactorThread) Thread.currentThread();
            setBufferPool(thread.getBufPool());
            this.init(length);
            mysql.switchNioHandler(this);
            onStart();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public int onPayloadLength() {
        return length;
    }


    public void onStart() {
        try {
            init(onPayloadLength());
            ByteBuffer header = buffers[0] = getBufferPool().allocate(4);
            boolean hasNext = false;
            while (hasNext = nextPacketInPacketSplitter()) {
                this.writeIndex = startIndex + getOffsetInPacketSplitter();
                setReminsPacketLen(getPacketLenInPacketSplitter());
                setPacketId((byte) (1 + getPacketId()));
                writeHeader(header, getPacketLenInPacketSplitter(), getPacketId());
                int writed = writePayload(buffers, this.writeIndex, getReminsPacketLen(), getServerSocket());
                this.writeIndex += writed;
                setReminsPacketLen(getReminsPacketLen() - writed);
                int reminsPacketLen = getReminsPacketLen();
                if (reminsPacketLen > 0) {
                    mysql.change2WriteOpts();
                    break;
                } else if (reminsPacketLen == 0) {
                    continue;
                }
            }
            if (!hasNext) {
                writeFinishedAndClear(mysql,true);
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
            onError(e);
        }
    }

    @Override
    public void onSocketWrite(AbstractMySQLClientSession session) throws IOException {
        int writed = writePayload(buffers, this.writeIndex, getReminsPacketLen(), getServerSocket());
        this.writeIndex += writed;
        setReminsPacketLen(getReminsPacketLen() - writed);
        while (getReminsPacketLen() == 0) {
            if (nextPacketInPacketSplitter()) {
                this.writeIndex = this.startIndex + getOffsetInPacketSplitter();
                setReminsPacketLen(getPacketLenInPacketSplitter());
                setPacketId((byte) (1 + getPacketId()));
                writed = writePayload(buffers, this.writeIndex, getReminsPacketLen(), getServerSocket());
                this.writeIndex += writed;
                setReminsPacketLen(getReminsPacketLen() - writed);
            } else {
                writeFinishedAndClear(mysql,true);
                return;
            }
        }
    }

    protected int writePayload(ByteBuffer[] buffer, int writeIndex, int reminsPacketLen, SocketChannel serverSocket) throws IOException {
        ByteBuffer body = buffer[1];
        body.position(writeIndex).limit(reminsPacketLen + writeIndex);
        ByteBuffer header = buffer[0];
        if (header.hasRemaining()) {
            serverSocket.write(buffer);
            return body.position() - writeIndex;
        } else {
            return serverSocket.write(body);
        }
    }
    public void writeFinishedAndClear(MySQLClientSession mysql,boolean success) {
        mysql.clearReadWriteOpts();
        getBufferPool().recycle(buffers[0]);
        buffers[0] = null;
        setServerSocket(null);
        ByteBuffer buffer = buffers[1];
        onWriteFinished(mysql,(T) buffer, success);
    }

    <T extends ByteBuffer> void writeHeader(T buffer, int packetLen, int packerId) {
        buffer.position(0);
        MySQLPacket.writeFixIntByteBuffer(buffer, 3, packetLen);
        buffer.put((byte) packerId);
        buffer.position(0);
    }

    void onWriteFinished(MySQLClientSession mysql,T buffer, boolean success) {
        AsynTaskCallBack callBackAndReset = mysql.getCallBackAndReset();
        try {
            clearResource(buffer);
            callBackAndReset.finished(this.mysql, this, success, null, null);
        } catch (Exception e) {
            mysql.setLastThrowable(e);
            callBackAndReset.finished(this.mysql, this, false, null, null);
        }
    }

    abstract void clearResource(T f) throws Exception;

    void onError(Throwable e) {
        mysql.setLastThrowable(e);
        writeFinishedAndClear(mysql,false);
    }

    public byte getPacketId() {
        return mysql.getPacketId();
    }


    public void setPacketId(byte packetId) {
        mysql.setPacketId(packetId);
    }


    public BufferPool getBufferPool() {
        return bufferPool;
    }


    public void setBufferPool(BufferPool bufferPool) {
        this.bufferPool = bufferPool;
    }


    public SocketChannel getServerSocket() {
        return socketChannel;
    }


    public void setServerSocket(SocketChannel serverSocket) {
        this.socketChannel = serverSocket;
    }


    public int getReminsPacketLen() {
        return reminsPacketLen;
    }


    public void setReminsPacketLen(int reminsPacketLen) {
        this.reminsPacketLen = reminsPacketLen;
    }


    public int getTotalSizeInPacketSplitter() {
        return totalSize;
    }


    public void setTotalSizeInPacketSplitter(int totalSize) {
        this.totalSize = totalSize;
    }



    public void setPacketLenInPacketSplitter(int currentPacketLen) {
        this.currentPacketLen = currentPacketLen;
    }


    public void setOffsetInPacketSplitter(int offset) {
        this.offset = offset;
    }


    public int getPacketLenInPacketSplitter() {
        return currentPacketLen;
    }


    public int getOffsetInPacketSplitter() {
        return offset;
    }



    public void onSocketClosed(MySQLClientSession session, boolean normal) {
        if (!normal) {
            onError(getSessionCaller().getLastThrowableAndReset());
        } else {
            writeFinishedAndClear(mysql,false);
        }
    }

    @Override
    public void onSocketRead(AbstractMySQLClientSession session) throws IOException {

    }

    @Override
    public void onWriteFinished(AbstractMySQLClientSession session) throws IOException {

    }

    @Override
    public void onSocketClosed(AbstractMySQLClientSession session, boolean normal) {

    }
}