package com.edgedb.binary;

import com.edgedb.binary.packets.shared.Annotation;
import com.edgedb.binary.packets.shared.KeyValue;
import com.edgedb.util.BinaryProtocolUtils;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.Nullable;
import org.joou.UByte;
import org.joou.UInteger;
import org.joou.ULong;
import org.joou.UShort;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.joou.Unsigned.*;

public class PacketReader {
    private final ByteBuf buffer;
    private static final Map<Class<?>, Function<PacketReader, ?>> numberReaderMap;

    private final int initPos;

    public PacketReader(ByteBuf buffer) {
        this.buffer = buffer;
        this.initPos = buffer.readerIndex();
    }

    static {
        numberReaderMap = new HashMap<>();
        numberReaderMap.put(Byte.TYPE, PacketReader::readByte);
        numberReaderMap.put(Short.TYPE, PacketReader::readInt16);
        numberReaderMap.put(Integer.TYPE, PacketReader::readInt32);
        numberReaderMap.put(Long.TYPE, PacketReader::readInt64);
        numberReaderMap.put(UByte.class, PacketReader::readUByte);
        numberReaderMap.put(UShort.class, PacketReader::readUInt16);
        numberReaderMap.put(UInteger.class, PacketReader::readUInt32);
        numberReaderMap.put(ULong.class, PacketReader::readUInt64);
        numberReaderMap.put(Float.TYPE, PacketReader::readFloat);
        numberReaderMap.put(Double.TYPE, PacketReader::readDouble);
    }

    public int position() {
        return buffer.readerIndex() - this.initPos;
    }

    public int size() {
        return position() + this.buffer.readableBytes();
    }

    public void skip(int count) {
        this.buffer.skipBytes(count);
    }

    public boolean isEmpty() {
        return this.buffer.readableBytes() == 0; // TODO: this doesn't look right?
    }

    public byte[] consumeByteArray() {
        var arr = new byte[this.buffer.readableBytes()];
        this.buffer.readBytes(arr);
        return arr;
    }

    public UUID readUUID() {
        return new UUID(buffer.readLong(), buffer.readLong());
    }

    public String readString() {
        var len = readInt32();
        var buffer = new byte[len];
        this.buffer.readBytes(buffer);
        return new String(buffer, StandardCharsets.UTF_8);
    }

    public boolean readBoolean() {
        return buffer.readBoolean();
    }

    public Byte readByte() {
        return buffer.readByte();
    }

    public UByte readUByte() {
        return ubyte(buffer.readByte());
    }

    public char readChar() {
        return buffer.readChar();
    }

    public double readDouble() {
        return buffer.readDouble();
    }

    public float readFloat() {
        return buffer.readFloat();
    }

    public long readInt64() {
        return buffer.readLong();
    }

    public ULong readUInt64() {
        return ulong(buffer.readLong());
    }

    public int readInt32() {
        return buffer.readInt();
    }

    public UInteger readUInt32() {
        return uint(buffer.readUnsignedInt());
    }

    public short readInt16() {
        return buffer.readShort();
    }

    public UShort readUInt16() {
        return ushort(buffer.readUnsignedShort());
    }

    public String[] readStringArray() {
        var count = readInt32();

        var arr = new String[count];

        for (int i = 0; i < count; i++) {
            arr[i] = readString();
        }

        return arr;
    }

    public @Nullable ByteBuf readByteArray() {
        var len = readInt32();

        if(len <= 0) {
            return null;
        }

        return readBytes(len);
    }

    public ByteBuf readBytes(int length) {
        return this.buffer.readRetainedSlice(length);
    }

    public Annotation[] readAnnotations() {
        return readArrayOf(Annotation.class, Annotation::new, UShort.class);
    }

    public KeyValue[] readAttributes() {
        return readArrayOf(KeyValue.class, KeyValue::new, UShort.class);
    }

    @SuppressWarnings("unchecked")
    public <U extends Number, T> T[] readArrayOf(Class<T> cls, Function<PacketReader, T> mapper, Class<U> lengthPrimitive) {
        var len = ((U)numberReaderMap.get(lengthPrimitive).apply(this)).intValue();

        // can only use 32 bit, so cast to that
        var arr = (T[]) Array.newInstance(cls, len);

        for(int i = 0; i < len; i++) {
            arr[i] = mapper.apply(this);
        }

        return arr;
    }

    @SuppressWarnings("unchecked")
    public <U extends Number, T extends Enum<T> & BinaryEnum<U>> T readEnum(Function<U, T> mapper, Class<U> primitive) {
        var value = (U)numberReaderMap.get(primitive).apply(this);
        return mapper.apply(value);
    }

    public <U extends Number, T extends Enum<T> & BinaryEnum<U>> EnumSet<T> readEnumSet(Class<T> cls, Class<U> primitive, Function<U, T> map) {
        var value = BinaryProtocolUtils.castNumber((Number) numberReaderMap.get(primitive).apply(this), primitive);

        var flagBits = Arrays.stream(cls.getEnumConstants())
                .map(BinaryEnum::getValue)
                .filter((u) -> {
                    // assume we can use 64 bit here, should not be any IEEE float/double numbers ever :)
                    return (u.longValue() & value.longValue()) == u.longValue();
                })
                .map(map);

        return EnumSet.copyOf(flagBits.collect(Collectors.toSet()));
    }
}