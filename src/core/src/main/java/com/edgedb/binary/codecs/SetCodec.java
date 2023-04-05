package com.edgedb.binary.codecs;

import com.edgedb.binary.PacketReader;
import com.edgedb.binary.PacketWriter;
import com.edgedb.exceptions.EdgeDBException;
import org.jetbrains.annotations.Nullable;

import javax.naming.OperationNotSupportedException;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import static com.edgedb.util.BinaryProtocolUtils.INT_SIZE;
import static com.edgedb.util.BinaryProtocolUtils.LONG_SIZE;

public final class SetCodec<T> extends CodecBase<Collection<T>> {
    private final Codec<T> innerCodec;

    @SuppressWarnings("unchecked")
    public SetCodec(Class<?> cls, Codec<?> innerCodec) {
        super((Class<Collection<T>>) cls);
        this.innerCodec = (Codec<T>) innerCodec;
    }

    @Override
    public void serialize(PacketWriter writer, @Nullable Collection<T> value, CodecContext context) throws OperationNotSupportedException {
        throw new OperationNotSupportedException();
    }

    @Override
    public @Nullable Collection<T> deserialize(PacketReader reader, CodecContext context) throws EdgeDBException, OperationNotSupportedException {
        return deserializeSet(
                reader,
                context,
                innerCodec instanceof ArrayCodec<?>
                        ? this::deserializeEnvelopeElement
                        : this::deserializeSetElement
        );
    }

    @SuppressWarnings("unchecked")
    private Collection<T> deserializeSet(PacketReader reader, CodecContext context, SetElementDeserializer<T> elementDeserializer) throws EdgeDBException, OperationNotSupportedException {
        var dimensions = reader.readInt32();

        reader.skip(LONG_SIZE); // flags & reserved

        if(dimensions == 0) {
            return Set.of();
        }

        if(dimensions != 1) {
            throw new EdgeDBException("Only dimensions of 1 are supported for sets");
        }

        var upper = reader.readInt32();
        var lower = reader.readInt32();

        var numElements = upper - lower + 1;

        var result = (T[])Array.newInstance(innerCodec.getConvertingClass(), numElements);

        for(int i = 0; i != numElements; i++) {
            result[i] = elementDeserializer.deserialize(reader, context);
        }

        return Arrays.asList(result);
    }

    private @Nullable T deserializeEnvelopeElement(PacketReader reader, CodecContext context) throws EdgeDBException, OperationNotSupportedException {
        reader.skip(INT_SIZE);

        var envelopeElements = reader.readInt32();

        if(envelopeElements != 1) {
            throw new EdgeDBException(String.format("Envelope should contain only one element, but this envelope contains %d", envelopeElements));
        }

        reader.skip(INT_SIZE);

        return innerCodec.deserialize(reader, context);
    }

    private @Nullable T deserializeSetElement(PacketReader reader, CodecContext context) throws EdgeDBException, OperationNotSupportedException {
        var data = reader.readByteArray();

        if(data == null) {
            return null;
        }

        return innerCodec.deserialize(new PacketReader(data), context);
    }

    @FunctionalInterface
    private interface SetElementDeserializer<T> {
        @Nullable T deserialize(PacketReader reader, CodecContext context) throws EdgeDBException, OperationNotSupportedException;
    }
}