package com.cshawn.plugin.repositorykeeper.request

import org.gradle.api.artifacts.Dependency

/**
 *
 * @author: C.Shawn
 * @date: 2021/5/15 9:52 PM
 */
class PomInfo (
    groupId: String,
    artifactId: String,
    version: String,
    var dependencies: List<DependencyInfo>?
): DependencyInfo(groupId, artifactId, version) {
    constructor(): this("", "", "", null)
}

open class DependencyInfo (
    var groupId: String,
    var artifactId: String,
    var version: String
) {
    constructor(dependency: Dependency) : this(
        dependency.group ?: "",
        dependency.name,
        dependency.version ?: ""
    )

    constructor() : this("", "", "")
}