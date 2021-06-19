package com.cshawn.plugin.repositorykeeper.request

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Streaming

/**
 *
 * @author: C.Shawn
 * @date: 2021/5/14 5:29 PM
 */
interface RequestService {

    @GET("{group}/{name}/{version}/{name}-{version}.{extension}")
    @Streaming
    fun getFile(
        @Path("group") group: String,
        @Path("name") name: String,
        @Path("version") version: String,
        @Path("extension") extension: String
    ) : Call<ResponseBody>

    @GET("{group}/{name}/{version}/{name}-{version}.{extension}")
    fun getContent(
        @Path("group") group: String,
        @Path("name") name: String,
        @Path("version") version: String,
        @Path("extension") extension: String
    ) : Call<ResponseBody>

    @GET("{group}/{name}/{version}/{name}-{version}-{classifier}.{extension}")
    fun getContent(
        @Path("group") group: String,
        @Path("name") name: String,
        @Path("version") version: String,
        @Path("classifier") classifier: String,
        @Path("extension") extension: String
    ) : Call<ResponseBody>

    @GET("{group}/{name}/maven-metadata.xml")
    fun getMetaData(
        @Path("group") group: String,
        @Path("name") name: String
    ) : Call<ResponseBody>

    @GET("{group}/{name}/{version}-SNAPSHOT/{name}-{snapshotVersion}.{extension}")
    @Streaming
    fun getSnapshotFile(
        @Path("group") group: String,
        @Path("name") name: String,
        @Path("version") version: String,
        @Path("snapshotVersion") snapshotVersion: String,
        @Path("extension") extension: String
    ) : Call<ResponseBody>


    @GET("{group}/{name}/{version}-SNAPSHOT/{name}-{snapshotVersion}.{extension}")
    fun getSnapshotContent(
        @Path("group") group: String,
        @Path("name") name: String,
        @Path("version") version: String,
        @Path("snapshotVersion") snapshotVersion: String,
        @Path("extension") extension: String
    ) : Call<ResponseBody>

    @GET("{group}/{name}/{version}-SNAPSHOT/{name}-{snapshotVersion}-{classifier}.{extension}")
    fun getSnapshotContent(
        @Path("group") group: String,
        @Path("name") name: String,
        @Path("version") version: String,
        @Path("snapshotVersion") snapshotVersion: String,
        @Path("classifier") classifier: String,
        @Path("extension") extension: String
    ) : Call<ResponseBody>

    @GET("{group}/{name}/{version}-SNAPSHOT/maven-metadata.xml")
    fun getSnapshotMetaData(
        @Path("group") group: String,
        @Path("name") name: String,
        @Path("version") version: String
    ) : Call<ResponseBody>
}