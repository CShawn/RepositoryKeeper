package com.cshawn.plugin.repositorykeeper.converter

/**
 * 依赖配置项
 * @author: C.Shawn
 * @date: 2021/5/14 10:55 AM
 */
open class DependencyConfig {
    var dependencyKey: String = "dependency"
    var mavenServer: String = ""
    var cachePath: String = ""
    var configTypes: Array<String> = emptyArray()
    var targetMavenDependency: (String) -> Boolean = { false }
    var localPathToMavenStr: (String) -> String = { it }
    var identifierToLocalPath: (String) -> String = { it }
    var customToLocalPath: (String) -> String = { it }
    var customMatchMaven: (customConfig: String, mavenIdentifier: String) -> Boolean = { _, _ -> false }
    var customMatchLocal: (customConfig: String, group: String, name: String) -> Boolean = { _, _, _ -> false }
}