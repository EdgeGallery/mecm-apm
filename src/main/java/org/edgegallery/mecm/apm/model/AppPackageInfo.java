/*
 *  Copyright 2021 Huawei Technologies Co., Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.edgegallery.mecm.apm.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "apppackageinfo")
public final class AppPackageInfo {

    @Id
    @Column(name = "id")
    private String appPkgInfoId;

    @Column(name = "app_id")
    private String appId;

    @Column(name = "package_id")
    private String packageId;

    @Column(name = "name")
    private String name;

    @Column(name = "appstore_endpoint")
    private String appstoreEndpoint;

    @Column(name = "pkg_size")
    private String size;

    @Column(name = "version")
    private String version;

    @Column(name = "type")
    private String type;

    @Column(name = "affinity")
    private String affinity;

    @Column(name = "industry")
    private String industry;

    @Column(name = "contact")
    private String contact;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "status")
    private String status;

    @Column(name = "short_desc")
    private String shortDesc;

    @Column(name = "test_task_id")
    private String testTaskId;

    @Column(name = "provider")
    private String provider;

    @Column(name = "sync_status")
    private String syncStatus;

    @Column(name = "appstore_ip")
    private String appstoreIp;

    @Column(name = "created_time")
    private String createTime;

    @Column(name = "operational_info")
    private String operationalInfo;
}
