package com.edgedb.binary.codecs.scalars;

import com.edgedb.binary.PacketWriter;
import com.edgedb.binary.PacketReader;
import com.edgedb.binary.codecs.CodecContext;
import org.jetbrains.annotations.Nullable;

import javax.naming.OperationNotSupportedException;
import java.nio.charset.StandardCharsets;

public final class TextCodec extends ScalarCodecBase<String> {
    public TextCodec() {
        super(String.class);
    }

    @Override
    public void serialize(PacketWriter writer, @Nullable String value, CodecContext context) throws OperationNotSupportedException {
        if(value != null) {
            writer.writeArrayWithoutLength(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Override
    public String deserialize(PacketReader reader, CodecContext context) {
        return new String(reader.consumeByteArray(), StandardCharsets.UTF_8);
    }
}