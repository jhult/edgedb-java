package com.edgedb.binary.packets.shared;

import com.edgedb.binary.PacketWriter;
import com.edgedb.binary.PacketReader;
import com.edgedb.binary.SerializableData;
import com.edgedb.util.BinaryProtocolUtils;
import io.netty.buffer.ByteBuf;

import javax.naming.OperationNotSupportedException;

import static com.edgedb.util.BinaryProtocolUtils.sizeOf;

public class KeyValue implements SerializableData {
    public final short code;
    public final ByteBuf value;

    public KeyValue(short code, ByteBuf value) {
        this.code = code;
        this.value = value;
    }

    public KeyValue(PacketReader reader) {
        this.code = reader.readInt16();
        this.value = reader.readByteArray();
    }

    @Override
    public void write(PacketWriter writer) throws OperationNotSupportedException {
        writer.write(code);
        writer.writeArray(value);
    }

    @Override
    public int getSize() {
        return BinaryProtocolUtils.SHORT_SIZE + BinaryProtocolUtils.sizeOf(value);
    }
}