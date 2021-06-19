package com.cshawn.plugin.repositorykeeper

/**
 *
 * @author: C.Shawn
 * @date: 2021/5/20 11:46 AM
 */
object LogUtil {
    private const val tag = "dependency-manager"

    fun info(str: Any?) {
        println("$tag $str")
    }
}