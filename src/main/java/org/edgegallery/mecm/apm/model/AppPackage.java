/*
 *  Copyright 2020 Huawei Technologies Co., Ltd.
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

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "apppackage")
public class AppPackage {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "app_pkg_id")
    private String appPkgId;

    @Column(name = "app_pkg_name")
    private String appPkgName;

    @Column(name = "app_pkg_version")
    private String appPkgVersion;

    @Column(name = "app_pkg_path")
    private String appPkgPath;

    @Column(name = "app_provider")
    private String appProvider;

    @Column(name = "app_pkg_desc")
    private String appPkgDesc;

    @Column(name = "app_pkg_affinity")
    private String appPkgAffinity;

    @Column(name = "app_icon_url")
    private String appIconUrl;

    @Column(name = "created_time")
    private String createdTime;

    @Column(name = "modified_time")
    private String modifiedTime;

    @Column(name = "app_id")
    private String appId;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "local_file_path")
    private String localFilePath;
}
