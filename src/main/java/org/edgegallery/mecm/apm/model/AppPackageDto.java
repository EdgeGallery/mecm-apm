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

import java.util.LinkedList;
import java.util.List;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Application package schema.
 */
@Getter
@Setter
@ToString
public class AppPackageDto {

    @NotEmpty(message = "appPkgId is empty")
    private String appPkgId;

    @NotEmpty(message = "appPkgName is empty")
    private String appPkgName;

    @NotEmpty(message = "appPkgVersion is empty")
    private String appPkgVersion;

    @NotEmpty(message = "appPkgPath is empty")
    private String appPkgPath;

    @NotNull(message = "appProvider is null")
    private String appProvider;

    @NotNull(message = "appPkgDesc is null")
    private String appPkgDesc;

    @NotNull(message = "appPkgAffinity is null")
    private String appPkgAffinity;

    @NotNull(message = "appIconUrl is null")
    private String appIconUrl;

    @NotNull(message = "createdTime is null")
    private String createdTime;

    @NotNull(message = "modifiedTime is null")
    private String modifiedTime;

    @NotEmpty(message = "appId is empty")
    private String appId;

    @NotEmpty(message = "mecHost is empty")
    private List<String> mecHost = new LinkedList<>();

    @NotNull(message = "mecHostInfo is null")
    private List<MecHostDto> mecHostInfo = new LinkedList<>();

    /**
     * Constructor to create appPkgDto.
     *
     * @param appPkgId app package id
     * @param appPkgName app package name
     * @param appPkgVersion app package version
     * @param appPkgPath app package path
     * @param appProvider app package provider
     * @param appPkgDesc app package description
     * @param appPkgAffinity app package affinity
     * @param appIconUrl app package path to download icon
     * @param createdTime created time
     * @param modifiedTime modified time
     * @param appId app id
     * @param mecHost mec host ip
     * @param mecHostInfo mec host info
     */
    public AppPackageDto(@NotEmpty(message = "appPkgId is empty") String appPkgId,
                         @NotEmpty(message = "appPkgName is empty") String appPkgName,
                         @NotEmpty(message = "appPkgVersion is empty") String appPkgVersion,
                         @NotEmpty(message = "appPkgPath is empty") String appPkgPath,
                         @NotNull(message = "appProvider is null") String appProvider,
                         @NotNull(message = "appPkgDesc is null") String appPkgDesc,
                         @NotNull(message = "appPkgAffinity is null") String appPkgAffinity,
                         @NotNull(message = "appIconUrl is null") String appIconUrl,
                         @NotNull(message = "createdTime is null") String createdTime,
                         @NotNull(message = "modifiedTime is null") String modifiedTime,
                         @NotEmpty(message = "appId is empty") String appId,
                         @NotEmpty(message = "mecHost is empty") List<String> mecHost,
                         @NotNull(message = "mecHostInfo is null") List<MecHostDto> mecHostInfo) {
        this.appPkgId = appPkgId;
        this.appPkgName = appPkgName;
        this.appPkgVersion = appPkgVersion;
        this.appPkgPath = appPkgPath;
        this.appProvider = appProvider;
        this.appPkgDesc = appPkgDesc;
        this.appPkgAffinity = appPkgAffinity;
        this.appIconUrl = appIconUrl;
        this.createdTime = createdTime;
        this.modifiedTime = modifiedTime;
        this.appId = appId;
        this.mecHost = mecHost;
        this.mecHostInfo = mecHostInfo;
    }
}
