package org.edgegallery.mecm.apm.apihandler;

import org.edgegallery.mecm.apm.model.dto.AppPackageDeletedDto;
import org.edgegallery.mecm.apm.model.dto.AppPackageHostDeletedDto;
import org.edgegallery.mecm.apm.model.dto.AppPackageRecordDto;
import org.edgegallery.mecm.apm.model.dto.SyncUpdatedAppPackageDto;
import org.edgegallery.mecm.apm.service.RestServiceImpl;
import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class ApmSyncHandlerTest {

    private static final String TENANT_ID = "18db0283-3c67-4042-a708-a8e4a10c6b35";
    private static final String PACKAGE_ID = "f50358433cf8eb4719a62a49ed118c9b";
    private static final String ACCESS_TOKEN = "access_token";

    @InjectMocks
    ApmSyncHandler apmSyncHandler;
    AppPackageRecordDto appPackageRecordDto= new AppPackageRecordDto();
    AppPackageDeletedDto appPackageDeletedDto=new AppPackageDeletedDto();
    AppPackageHostDeletedDto appPackageHostDeletedDto= new AppPackageHostDeletedDto();

    @Mock
    private RestServiceImpl restServiceImpl;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }


    @Test(expected = Exception.class)
    public void testGetSyncPackageUpdateRecords() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Object[] obj1 = {TENANT_ID, "abdhjk", ACCESS_TOKEN};
        Method method1 = ApmSyncHandler.class.getDeclaredMethod("getSyncPackageUpdateRecords", String.class, String.class, String.class);
        method1.setAccessible(true);
        try {
            restServiceImpl.syncRecords("a", SyncUpdatedAppPackageDto.class, "b");
        } catch (Exception e) {
            assertTrue(true);
        }
        method1.invoke(apmSyncHandler, obj1);
    }

    @Test(expected = Exception.class)
    public void testUpdateSyncAppPackageRecords() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        appPackageRecordDto.setPackageId(PACKAGE_ID);
        Object[] obj1 = {TENANT_ID, appPackageRecordDto};
        Method method1 = ApmSyncHandler.class.getDeclaredMethod("updateSyncAppPackageRecords", String.class, AppPackageRecordDto.class);
        method1.setAccessible(true);
        method1.invoke(apmSyncHandler, obj1);
    }

    @Test(expected = Exception.class)
    public void testSyncPackageMgmtDataFromEdges() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Object[] obj1 = {TENANT_ID, ACCESS_TOKEN};
        Method method1 = ApmSyncHandler.class.getDeclaredMethod("synchronizePackageMgmtDataFromEdges", String.class, String.class);
        method1.setAccessible(true);
        method1.invoke(apmSyncHandler, obj1);
    }

    @Test(expected = Exception.class)
    public void testGetSyncPackageStaleRecords() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Object[] obj1 = {"appLcmEndPoint", TENANT_ID, ACCESS_TOKEN};
        Method method1 = ApmSyncHandler.class.getDeclaredMethod("getSyncPackageStaleRecords", String.class, String.class, String.class);
        method1.setAccessible(true);
        method1.invoke(apmSyncHandler, obj1);
    }

    @Test(expected = Exception.class)
    public void testDeleteSyncAppPackageRecords() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        appPackageDeletedDto.setAppPackageId(PACKAGE_ID);
        Object[] obj1 = {TENANT_ID, appPackageDeletedDto};
        Method method1 = ApmSyncHandler.class.getDeclaredMethod("deleteSyncAppPackageRecords", String.class, AppPackageDeletedDto.class);
        method1.setAccessible(true);
        method1.invoke(apmSyncHandler, obj1);
    }

    @Test(expected = Exception.class)
    public void testDeleteSyncAppPackageHostRecords() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        appPackageHostDeletedDto.setPackageId(PACKAGE_ID);
        Object[] obj1 = {TENANT_ID, appPackageHostDeletedDto};
        Method method1 = ApmSyncHandler.class.getDeclaredMethod("deleteSyncAppPackageHostRecords", String.class, AppPackageHostDeletedDto.class);
        method1.setAccessible(true);
        method1.invoke(apmSyncHandler, obj1);
    }

    @Test(expected = Exception.class)
    public void testGetInventoryApplcmCfg() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Object[] obj1 = {"1.1.1.1", ACCESS_TOKEN};
        Method method1 = ApmSyncHandler.class.getDeclaredMethod("getInventoryApplcmCfg", String.class, String.class);
        method1.setAccessible(true);
        method1.invoke(apmSyncHandler, obj1);
    }

}
