/*
 *    Copyright 2020-2021 Huawei Technologies Co., Ltd.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.edgegallery.mecm.apm.model;


import org.edgegallery.mecm.apm.model.dto.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class AppDtoTest {

    @InjectMocks
    AppPackageDeletedDto appPackageDeletedDto = new AppPackageDeletedDto();
    AppPackageHostDeletedDto appPackageHostDeletedDto = new AppPackageHostDeletedDto();
    AppPackageRecordDto appPackageRecordDto = new AppPackageRecordDto();
    SyncDeletedAppPackageDto syncDeletedAppPackageDto = new SyncDeletedAppPackageDto();
    SyncUpdatedAppPackageDto syncUpdatedAppPackageDto = new SyncUpdatedAppPackageDto();
    AppPackageDto appPackageDto = new AppPackageDto();

    @Before
    public void setUp() {
        appPackageDeletedDto.setAppPackageId("appPackage_id");

        appPackageHostDeletedDto.setPackageId("package_id");
        appPackageHostDeletedDto.setHostIp("host_id");

        appPackageRecordDto.setPackageId("package_id");

        List delList = new ArrayList<AppPackageDeletedDto>();
        delList.add(appPackageDeletedDto.getAppPackageId());
        syncDeletedAppPackageDto.setAppPackageStaleRec(delList);

        List hostDelList = new ArrayList<AppPackageHostDeletedDto>();
        hostDelList.add(appPackageHostDeletedDto.getPackageId());
        hostDelList.add(appPackageHostDeletedDto.getHostIp());
        syncDeletedAppPackageDto.setAppPackageHostStaleRec(hostDelList);

        List recList = new ArrayList<AppPackageRecordDto>();
        recList.add(appPackageRecordDto.getPackageId());
        syncUpdatedAppPackageDto.setAppPackageRecord(recList);
    }

    @Test
    public void testAppDnsProcessFlowResponse() {
        Assert.assertEquals("appPackage_id", appPackageDeletedDto.getAppPackageId());
        Assert.assertNotNull(appPackageDeletedDto.toString());

        Assert.assertEquals("package_id", appPackageHostDeletedDto.getPackageId());
        Assert.assertEquals("host_id", appPackageHostDeletedDto.getHostIp());
        Assert.assertNotNull(appPackageHostDeletedDto.toString());

        Assert.assertEquals("package_id", appPackageRecordDto.getPackageId());
        Assert.assertNotNull(appPackageRecordDto.toString());

        Assert.assertNotNull(syncDeletedAppPackageDto.getAppPackageStaleRec());
        Assert.assertNotNull(syncDeletedAppPackageDto.toString());
        Assert.assertNotNull(syncDeletedAppPackageDto.getAppPackageHostStaleRec());

        Assert.assertNotNull(syncUpdatedAppPackageDto.getAppPackageRecord());
        Assert.assertNotNull(syncUpdatedAppPackageDto.toString());

        Assert.assertNotNull(appPackageDto.toString());
    }
}
