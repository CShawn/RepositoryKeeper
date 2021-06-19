package com.cshawn.plugin.repositorykeeper.converter

import com.cshawn.plugin.repositorykeeper.DependencyUtil
import com.cshawn.plugin.repositorykeeper.LogUtil
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import java.io.File
import java.lang.IllegalStateException

/**
 * 将当前依赖类型转换成release依赖类型
 * 在直接依赖target-source的源码工程中，将gradle文件中的source依赖修改为maven-release依赖
 * @author: C.Shawn
 * @date: 2021/5/17 10:10 PM
 */
open class ReleaseConverter(
    project: Project
) : DependencyConverter(project) {
    override fun convert(customConfigs: Set<String>) {
        LogUtil.info("need convert to release: $lineSeparator${customConfigs.joinToString(lineSeparator)}")
        changeLocalToMaven(customConfigs)
    }

    protected open fun changeLocalToMaven(customConfigs: Set<String>) {
        val changeList = mutableSetOf<String>()
        project.rootProject.subprojects.forEach { sub ->
            sub.configurations.all { c ->
                if (c.name in DependencyUtil.dependencyConfig.configTypes) {
                    c.dependencies.all { localProject ->
                        customConfigs.forEach { config ->
                            if (localProject is ProjectDependency && DependencyUtil.dependencyConfig.customMatchLocal(
                                    config,
                                    localProject.group ?: "",
                                    localProject.name
                                )
                            ) {
                                LogUtil.info("affected: ${sub.name} -> $config")
                                changeList.add(sub.buildDir.parent)
                            }
                        }
                    }
                }
            }
        }
        project.gradle.buildFinished {
            if (changeList.isEmpty()) {
                LogUtil.info("no convert to release")
                return@buildFinished
            }
            val iterator = changeList.iterator()
            while (iterator.hasNext()) {
                val subDir = iterator.next()
                val gradle = File(subDir, Project.DEFAULT_BUILD_FILE)
                val lines = DependencyUtil.readStringFromFile(gradle).split(lineSeparator)
                    .toMutableList()
                val replaces = mutableListOf<Triple<Int, String, String>>()
                customConfigs.forEach { target ->
                    val localPath = DependencyUtil.dependencyConfig.customToLocalPath(target)
                    val mavenIdentifier = DependencyUtil.dependencyConfig.localPathToMavenStr(localPath)
                    lines.forEachIndexed { index, s ->
                        val str = s.trim()
                        if (!str.startsWith(lineComment) &&
                            str.endsWith("project(\'$localPath\')") ||
                            str.endsWith("project(\"$localPath\")")
                        ) {
                            replaces.add(
                                Triple(
                                    index,
                                    if (str.endsWith("project(\'$localPath\')")) "project(\'$localPath\')"
                                    else "project(\"$localPath\")",
                                    "\"$mavenIdentifier\""
                                )
                            )
                        }
                    }
                }
                if (replaces.isNotEmpty()) {
                    replaces.forEach {
                        lines[it.first] = lines[it.first].replace(it.second, it.third)
                    }
                    DependencyUtil.writeStringToFile(
                        gradle,
                        lines.joinToString(lineSeparator)
                    )
                } else {
                    iterator.remove()
                }
            }
            if (changeList.isNotEmpty()) {
                throw IllegalStateException("changed gradle file, please sync gradle manually: $lineSeparator${
                    changeList.joinToString(lineSeparator) { "$it${File.separator}build.gradle" }
                }")
            } else {
                LogUtil.info("no convert to release")
            }
        }
    }
}