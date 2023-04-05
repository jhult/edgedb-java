package com.edgedb.binary.packets.receivable;

import com.edgedb.Capabilities;
import com.edgedb.binary.packets.shared.Annotation;
import com.edgedb.binary.PacketReader;
import com.edgedb.binary.packets.ServerMessageType;
import io.netty.buffer.ByteBuf;

import java.util.EnumSet;
import java.util.UUID;

public class CommandComplete implements Receivable {
    public final EnumSet<Capabilities> capabilities;
    public final UUID stateTypeDescriptorId;
    public final String status;
    public final ByteBuf stateData;
    public final Annotation[] annotations;

    public CommandComplete(PacketReader reader) {
        this.annotations = reader.readAnnotations();
        this.capabilities = reader.readEnumSet(Capabilities.class, Long.TYPE, Capabilities::valueOf);
        this.status = reader.readString();
        this.stateTypeDescriptorId = reader.readUUID();
        this.stateData = reader.readByteArray();
    }

    @Override
    public ServerMessageType getMessageType() {
        return ServerMessageType.COMMAND_COMPLETE;
    }
}