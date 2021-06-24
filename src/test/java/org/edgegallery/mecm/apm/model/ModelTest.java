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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class ModelTest {

    @InjectMocks
    SwImageDescr swImageDescr = new SwImageDescr();
    ImageLocation imageLocation=new ImageLocation();
    AppRepo appRepo= new AppRepo();
    AppStore appStore= new AppStore();
    AppPackageInfo appPackageInfo=new AppPackageInfo();
    MecHost mecHost=new MecHost();
    PkgSyncInfo pkgSyncInfo=new PkgSyncInfo();
    AppPackageSyncInfo appPackageSyncInfo=new AppPackageSyncInfo();
    AppTemplate appTemplate = new AppTemplate();

    @Before
    public void setup(){
        swImageDescr.setId("id");
        swImageDescr.setName("name");
        swImageDescr.setVersion("version");
        swImageDescr.setChecksum("checksum");
        swImageDescr.setContainerFormat("containerFormat");
        swImageDescr.setDiskFormat("diskFormat");
        swImageDescr.setMinDisk("minDisk");
        swImageDescr.setMinRam("minRam");
        swImageDescr.setArchitecture("architecture");
        swImageDescr.setSize("size");
        swImageDescr.setSwImage("swImage");
        swImageDescr.setOperatingSystem("operatingSystem");
        swImageDescr.setSupportedVirtualisationEnvironment("supportedVirtualisationEnvironment");

        imageLocation.setDomainname("domainname");
        imageLocation.setProject("project");

        appRepo.setRepoEndPoint("endpoint");

        appStore.setTenantId("tenantId");
        appStore.setAppstoreName("appstoreName");

        appPackageInfo.setAppstoreIp("appstore_ip");

        mecHost.setAppPkgId("app_pkg_id");
        mecHost.setTenantId("tenant_id");

        Map<String,AppRepo> repoMap = new HashMap();
        repoMap.put("repoInfo" , appRepo);
        pkgSyncInfo.setRepoInfo(repoMap);

        appPackageSyncInfo.setRepoInfo(repoMap);

        appTemplate.setAppId("appId");
        appTemplate.setAppPackageId("appPackageId");
        appTemplate.setAppPkgName("appPackageName");
        appTemplate.setTenantId("tenantId");
        appTemplate.setVersion("version");
        appTemplate.setId("id");
    }

    @Test
    public void testAppDnsProcessFlowResponse() {
        Assert.assertEquals("id", swImageDescr.getId());
        Assert.assertEquals("name", swImageDescr.getName());
        Assert.assertEquals("version", swImageDescr.getVersion());
        Assert.assertEquals("checksum", swImageDescr.getChecksum());
        Assert.assertEquals("containerFormat", swImageDescr.getContainerFormat());
        Assert.assertEquals("diskFormat", swImageDescr.getDiskFormat());
        Assert.assertEquals("minDisk", swImageDescr.getMinDisk());
        Assert.assertEquals("minRam", swImageDescr.getMinRam());
        Assert.assertEquals("architecture", swImageDescr.getArchitecture());
        Assert.assertEquals("size", swImageDescr.getSize());
        Assert.assertEquals("swImage", swImageDescr.getSwImage());
        Assert.assertEquals("operatingSystem", swImageDescr.getOperatingSystem());
        Assert.assertEquals("supportedVirtualisationEnvironment", swImageDescr.getSupportedVirtualisationEnvironment());
        Assert.assertNotNull(swImageDescr.toString());

        Assert.assertEquals("domainname", imageLocation.getDomainname());
        Assert.assertEquals("project", imageLocation.getProject());
        Assert.assertNotNull(imageLocation.toString());

        Assert.assertEquals("endpoint", appRepo.getRepoEndPoint());
        Assert.assertNotNull(appRepo.toString());

        Assert.assertEquals("tenantId", appStore.getTenantId());
        Assert.assertEquals("appstoreName", appStore.getAppstoreName());
        Assert.assertNotNull(appStore.toString());

        Assert.assertEquals("appstore_ip", appPackageInfo.getAppstoreIp());
        Assert.assertNotNull(appPackageInfo.toString());

        Assert.assertEquals("app_pkg_id", mecHost.getAppPkgId());
        Assert.assertEquals("tenant_id", mecHost.getTenantId());

        Assert.assertNotNull(pkgSyncInfo.getRepoInfo());
        Assert.assertNotNull(pkgSyncInfo.toString());
        Assert.assertNotNull(appPackageSyncInfo.toString());

        Assert.assertEquals("appId", appTemplate.getAppId());
        Assert.assertEquals("appPackageId", appTemplate.getAppPackageId());
        Assert.assertEquals("appPackageName", appTemplate.getAppPkgName());
        Assert.assertEquals("tenantId", appTemplate.getTenantId());
        Assert.assertEquals("version", appTemplate.getVersion());
        Assert.assertEquals("id", appTemplate.getId());
        Assert.assertNotNull(appTemplate.toString());

    }

    }
