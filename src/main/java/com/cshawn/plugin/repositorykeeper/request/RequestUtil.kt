package com.cshawn.plugin.repositorykeeper.request

import com.cshawn.plugin.repositorykeeper.DependencyUtil
import retrofit2.Retrofit

/**
 *
 * @author: C.Shawn
 * @date: 2021/5/15 7:57 PM
 */
object RequestUtil {
    private val retrofits = mutableMapOf<String, RequestService>()
    fun getRequestService(baseUrl: String = DependencyUtil.dependencyConfig.mavenServer): RequestService {
        return retrofits[baseUrl] ?: Retrofit.Builder()
            .baseUrl(baseUrl)
            .build()
            .create(RequestService::class.java)
            .also { retrofits[baseUrl] = it }
    }
}