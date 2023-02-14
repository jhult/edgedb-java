package com.edgedb.driver.binary.packets.sendables;

import com.edgedb.driver.binary.PacketWriter;
import com.edgedb.driver.binary.packets.ClientMessageType;
import com.edgedb.driver.util.BinaryProtocolUtils;

import javax.naming.OperationNotSupportedException;
import java.nio.ByteBuffer;

public class AuthenticationSASLInitialResponse extends Sendable {
    private final String method;
    private final ByteBuffer payload;

    public AuthenticationSASLInitialResponse(ByteBuffer payload, String method) {
        super(ClientMessageType.AUTHENTICATION_SASL_INITIAL_RESPONSE);
        this.method = method;
        this.payload = payload;
    }

    @Override
    protected void buildPacket(PacketWriter writer) throws OperationNotSupportedException {
        writer.write(method);
        writer.writeArray(payload);
    }

    @Override
    public int getSize() {
        return BinaryProtocolUtils.sizeOf(this.method) + BinaryProtocolUtils.sizeOf(this.payload);
    }
}
