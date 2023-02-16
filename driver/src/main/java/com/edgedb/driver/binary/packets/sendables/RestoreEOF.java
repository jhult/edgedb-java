package com.edgedb.driver.binary.packets.sendables;

import com.edgedb.driver.binary.PacketWriter;
import com.edgedb.driver.binary.packets.ClientMessageType;

import javax.naming.OperationNotSupportedException;

public class RestoreEOF extends Sendable{
    public RestoreEOF() {
        super(ClientMessageType.RESTORE_EOF);
    }

    @Override
    public int getDataSize() {
        return 0;
    }

    @Override
    protected void buildPacket(PacketWriter writer) throws OperationNotSupportedException { /* no data */ }
}
