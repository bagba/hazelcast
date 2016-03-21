/*
 * Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.replicatedmap.impl.operation;

import com.hazelcast.cluster.memberselector.MemberSelectors;
import com.hazelcast.core.Member;
import com.hazelcast.nio.Address;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import com.hazelcast.replicatedmap.impl.ReplicatedMapService;
import com.hazelcast.replicatedmap.impl.record.ReplicatedRecordStore;
import com.hazelcast.spi.AbstractOperation;
import com.hazelcast.spi.OperationService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import static com.hazelcast.replicatedmap.impl.ReplicatedMapService.INVOCATION_TRY_COUNT;

/**
 * This operation will execute the remote clear on replicated map if
 * {@link com.hazelcast.core.ReplicatedMap#clear()} is called.
 */
public class ClearOperation extends AbstractOperation implements IdentifiedDataSerializable {

    private String mapName;
    private boolean replicateClear;
    private transient int response;

    public ClearOperation() {
    }

    public ClearOperation(String mapName, boolean replicateClear) {
        this.mapName = mapName;
        this.replicateClear = replicateClear;
    }

    @Override
    public void run() throws Exception {
        if (getNodeEngine().getConfig().isLiteMember()) {
            return;
        }
        ReplicatedMapService service = getService();
        ReplicatedRecordStore store = service.getReplicatedRecordStore(mapName, false, getPartitionId());
        if (store == null) {
            return;
        }
        response = store.size();
        store.clear();
        if (replicateClear) {
            replicateClearOperation();
        }
    }

    protected void replicateClearOperation() {
        final OperationService operationService = getNodeEngine().getOperationService();
        Collection<Address> members = getMemberAddresses();
        for (Address address : members) {
            ClearOperation clearOperation = new ClearOperation(mapName, false);
            clearOperation.setPartitionId(getPartitionId());
            clearOperation.setValidateTarget(false);
            operationService
                    .createInvocationBuilder(getServiceName(), clearOperation, address)
                    .setTryCount(INVOCATION_TRY_COUNT)
                    .invoke();
        }
    }

    protected Collection<Address> getMemberAddresses() {
        Address thisAddress = getNodeEngine().getThisAddress();
        Collection<Member> members = getNodeEngine().getClusterService().getMembers(MemberSelectors.DATA_MEMBER_SELECTOR);
        Collection<Address> addresses = new ArrayList<Address>();
        for (Member member : members) {
            Address address = member.getAddress();
            if (address.equals(thisAddress)) {
                continue;
            }
            addresses.add(address);
        }
        return addresses;
    }


    @Override
    public boolean returnsResponse() {
        return true;
    }

    @Override
    public Object getResponse() {
        return response;
    }

    @Override
    public String getServiceName() {
        return ReplicatedMapService.SERVICE_NAME;
    }

    @Override
    public int getFactoryId() {
        return ReplicatedMapDataSerializerHook.F_ID;
    }

    @Override
    public int getId() {
        return ReplicatedMapDataSerializerHook.OP_CLEAR;
    }

    @Override
    protected void writeInternal(ObjectDataOutput out) throws IOException {
        super.writeInternal(out);
        out.writeUTF(mapName);
        out.writeBoolean(replicateClear);
    }

    @Override
    protected void readInternal(ObjectDataInput in) throws IOException {
        super.readInternal(in);
        mapName = in.readUTF();
        replicateClear = in.readBoolean();
    }
}
