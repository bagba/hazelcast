/*
 * Copyright (c) 2008-2018, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.internal.partition;

import com.hazelcast.internal.cluster.Versions;
import com.hazelcast.internal.cluster.impl.ClusterDataSerializerHook;
import com.hazelcast.nio.Address;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import com.hazelcast.nio.serialization.impl.Versioned;
import com.hazelcast.util.UuidUtil;
import com.hazelcast.version.Version;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class MigrationInfo implements IdentifiedDataSerializable, Versioned {

    public enum MigrationStatus {

        ACTIVE(0),
        INVALID(1),
        SUCCESS(2),
        FAILED(3);

        private final int code;

        MigrationStatus(int code) {
            this.code = code;
        }

        public static void writeTo(MigrationStatus type, DataOutput out) throws IOException {
            out.writeByte(type.code);
        }

        public static MigrationStatus readFrom(DataInput in) throws IOException {
            final byte code = in.readByte();
            switch (code) {
                case 0:
                    return ACTIVE;
                case 1:
                    return INVALID;
                case 2:
                    return SUCCESS;
                case 3:
                    return FAILED;
                default:
                    throw new IllegalArgumentException("Code: " + code);
            }
        }

    }

    private String uuid;
    private int partitionId;

    private PartitionReplica source;
    private PartitionReplica destination;
    private Address master;

    private int sourceCurrentReplicaIndex;
    private int sourceNewReplicaIndex;
    private int destinationCurrentReplicaIndex;
    private int destinationNewReplicaIndex;

    private final AtomicBoolean processing = new AtomicBoolean(false);
    private volatile MigrationStatus status;

    public MigrationInfo() {
    }

    public MigrationInfo(int partitionId, PartitionReplica source, PartitionReplica destination,
            int sourceCurrentReplicaIndex, int sourceNewReplicaIndex,
            int destinationCurrentReplicaIndex, int destinationNewReplicaIndex) {
        this.uuid = UuidUtil.newUnsecureUuidString();
        this.partitionId = partitionId;
        this.source = source;
        this.destination = destination;
        this.sourceCurrentReplicaIndex = sourceCurrentReplicaIndex;
        this.sourceNewReplicaIndex = sourceNewReplicaIndex;
        this.destinationCurrentReplicaIndex = destinationCurrentReplicaIndex;
        this.destinationNewReplicaIndex = destinationNewReplicaIndex;
        this.status = MigrationStatus.ACTIVE;
    }

    public PartitionReplica getSource() {
        return source;
    }

    public Address getSourceAddress() {
        return source != null ? source.address() : null;
    }

    public String getSourceUuid() {
        return source != null ? source.uuid() : null;
    }

    public PartitionReplica getDestination() {
        return destination;
    }

    public Address getDestinationAddress() {
        return destination != null ? destination.address() : null;
    }

    public String getDestinationUuid() {
        return destination != null ? destination.uuid() : null;
    }

    public int getPartitionId() {
        return partitionId;
    }

    public int getSourceCurrentReplicaIndex() {
        return sourceCurrentReplicaIndex;
    }

    public int getSourceNewReplicaIndex() {
        return sourceNewReplicaIndex;
    }

    public int getDestinationCurrentReplicaIndex() {
        return destinationCurrentReplicaIndex;
    }

    public int getDestinationNewReplicaIndex() {
        return destinationNewReplicaIndex;
    }

    public Address getMaster() {
        return master;
    }

    public MigrationInfo setMaster(Address master) {
        this.master = master;
        return this;
    }

    public boolean startProcessing() {
        return processing.compareAndSet(false, true);
    }

    public boolean isProcessing() {
        return processing.get();
    }

    public void doneProcessing() {
        processing.set(false);
    }

    public MigrationStatus getStatus() {
        return status;
    }

    public MigrationInfo setStatus(MigrationStatus status) {
        this.status = status;
        return this;
    }

    public boolean isValid() {
        return status != MigrationStatus.INVALID;
    }

    @Override
    public void writeData(ObjectDataOutput out) throws IOException {
        out.writeUTF(uuid);
        out.writeInt(partitionId);
        out.writeByte(sourceCurrentReplicaIndex);
        out.writeByte(sourceNewReplicaIndex);
        out.writeByte(destinationCurrentReplicaIndex);
        out.writeByte(destinationNewReplicaIndex);
        MigrationStatus.writeTo(status, out);

        Version version = out.getVersion();

        boolean hasSource = source != null;
        out.writeBoolean(hasSource);
        if (hasSource) {
            if (version.isGreaterOrEqual(Versions.V3_12)) {
                out.writeObject(source);
            } else {
                writePartitionReplicaLegacy(out, source);
            }
        }

        boolean hasDestination = destination != null;
        out.writeBoolean(hasDestination);
        if (hasDestination) {
            if (version.isGreaterOrEqual(Versions.V3_12)) {
                out.writeObject(destination);
            } else {
                writePartitionReplicaLegacy(out, destination);
            }
        }

        master.writeData(out);
    }

    private static void writePartitionReplicaLegacy(ObjectDataOutput out, PartitionReplica destination) throws IOException {
        destination.address().writeData(out);
        out.writeUTF(destination.uuid());
    }

    @Override
    public void readData(ObjectDataInput in) throws IOException {
        uuid = in.readUTF();
        partitionId = in.readInt();
        sourceCurrentReplicaIndex = in.readByte();
        sourceNewReplicaIndex = in.readByte();
        destinationCurrentReplicaIndex = in.readByte();
        destinationNewReplicaIndex = in.readByte();
        status = MigrationStatus.readFrom(in);

        Version version = in.getVersion();
        boolean hasSource = in.readBoolean();
        if (hasSource) {
            if (version.isGreaterOrEqual(Versions.V3_12)) {
                source = in.readObject();
            } else {
                source = readPartitionReplicaLegacy(in);
            }
        }

        boolean hasDestination = in.readBoolean();
        if (hasDestination) {
            if (version.isGreaterOrEqual(Versions.V3_12)) {
                destination = in.readObject();
            } else {
                destination = readPartitionReplicaLegacy(in);
            }
        }

        master = new Address();
        master.readData(in);
    }

    private static PartitionReplica readPartitionReplicaLegacy(ObjectDataInput in) throws IOException {
        Address address = new Address();
        address.readData(in);
        String uuid = in.readUTF();
        return new PartitionReplica(address, uuid);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MigrationInfo that = (MigrationInfo) o;

        return uuid.equals(that.uuid);

    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("MigrationInfo{");
        sb.append("uuid=").append(uuid);
        sb.append(", partitionId=").append(partitionId);
        sb.append(", source=").append(source);
        sb.append(", sourceCurrentReplicaIndex=").append(sourceCurrentReplicaIndex);
        sb.append(", sourceNewReplicaIndex=").append(sourceNewReplicaIndex);
        sb.append(", destination=").append(destination);
        sb.append(", destinationCurrentReplicaIndex=").append(destinationCurrentReplicaIndex);
        sb.append(", destinationNewReplicaIndex=").append(destinationNewReplicaIndex);
        sb.append(", master=").append(master);
        sb.append(", processing=").append(processing);
        sb.append(", status=").append(status);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public int getFactoryId() {
        return ClusterDataSerializerHook.F_ID;
    }

    @Override
    public int getId() {
        return ClusterDataSerializerHook.MIGRATION_INFO;
    }
}
