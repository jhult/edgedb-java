package com.edgedb.binary.codecs.scalars.complex;

import com.edgedb.binary.PacketWriter;
import com.edgedb.binary.codecs.ComplexCodec;
import com.edgedb.binary.codecs.RuntimeCodec;
import com.edgedb.binary.PacketReader;
import com.edgedb.binary.codecs.CodecContext;
import com.edgedb.binary.codecs.complex.ComplexCodecBase;
import com.edgedb.binary.codecs.complex.ComplexCodecConverter;
import com.edgedb.binary.codecs.scalars.ScalarCodec;
import com.edgedb.binary.codecs.scalars.ScalarCodecBase;
import com.edgedb.exceptions.EdgeDBException;
import org.jetbrains.annotations.Nullable;

import javax.naming.OperationNotSupportedException;

public abstract class ComplexScalarCodecBase<T> extends ComplexCodecBase<T> implements ScalarCodec<T> {
    @SuppressWarnings({"unchecked", "rawtypes"})
    public ComplexScalarCodecBase(Class<T> cls, ComplexCodecConverter<T, ?>... converters) {
        super(cls, (c, p, cv) -> new RuntimeScalarCodecImpl(c, p, cv), converters);
    }

}

final class RuntimeScalarCodecImpl<T, U> extends ScalarCodecBase<U> implements RuntimeCodec<U> {
    private final ComplexCodecBase<T> parent;
    private final ComplexCodecConverter<T, U> converter;


    public RuntimeScalarCodecImpl(Class<U> cls, ComplexCodecBase<T> parent, ComplexCodecConverter<T, U> converter) {
        super(cls);
        this.parent = parent;
        this.converter = converter;
    }

    @Override
    public void serialize(PacketWriter writer, @Nullable U value, CodecContext context) throws OperationNotSupportedException, EdgeDBException {
        var converted = value == null ? null : converter.from.apply(value);
        this.parent.serialize(writer, converted, context);
    }

    @Override
    public @Nullable U deserialize(PacketReader reader, CodecContext context) throws EdgeDBException, OperationNotSupportedException {
        var value = parent.deserialize(reader, context);
        return value == null ? null : converter.to.apply(value);
    }

    @Override
    public ComplexCodec<?> getBroker() {
        return this.parent;
    }
}