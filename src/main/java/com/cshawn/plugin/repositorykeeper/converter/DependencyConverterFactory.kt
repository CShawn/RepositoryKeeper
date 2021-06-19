package com.cshawn.plugin.repositorykeeper.converter

import org.gradle.api.Project
import java.lang.IllegalArgumentException

/**
 *
 * @author: C.Shawn
 * @date: 2021/5/17 10:12 PM
 */
object DependencyConverterFactory {
    private val converters = mutableMapOf<String, DependencyConverter>()

    fun getDependencyConverter(type: String, project: Project): DependencyConverter {
        return converters[type] ?: when (type) {
            DependencyType.DEPENDENCY_SOURCE -> SourceConverter(project)
            DependencyType.DEPENDENCY_SNAPSHOT -> SnapshotConverter(project)
            DependencyType.DEPENDENCY_RELEASE -> ReleaseConverter(project)
            else -> throw IllegalArgumentException("invalid dependency type: $type")
        }.also {
            converters[type] = it
        }
    }
}