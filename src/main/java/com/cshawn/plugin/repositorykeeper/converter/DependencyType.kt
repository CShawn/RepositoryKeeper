package com.cshawn.plugin.repositorykeeper.converter

/**
 * 组件依赖方式
 * @author: C.Shawn
 * @date: 2021/5/14 9:55 AM
 */
class DependencyType {
    companion object {
        const val DEPENDENCY_SOURCE = "source"
        const val DEPENDENCY_SNAPSHOT = "snapshot"
        const val DEPENDENCY_RELEASE = "release"
        val dependencyTypes = arrayOf(DEPENDENCY_SOURCE, DEPENDENCY_SNAPSHOT, DEPENDENCY_RELEASE)
    }
}