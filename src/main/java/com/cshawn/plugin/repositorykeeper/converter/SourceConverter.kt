package com.cshawn.plugin.repositorykeeper.converter

import com.cshawn.plugin.repositorykeeper.DependencyUtil
import com.cshawn.plugin.repositorykeeper.LogUtil
import com.cshawn.plugin.repositorykeeper.request.DependencyInfo
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.initialization.Settings
import java.io.File
import java.lang.IllegalStateException

/**
 * 将当前依赖类型转换成source依赖类型
 * 所有直接间接依赖target-maven的maven库,动态替换为source依赖; 打开setting.gradle中的项目依赖
 * @author: C.Shawn
 * @date: 2021/5/17 10:19 PM
 */
class SourceConverter(
    project: Project
) : DependencyConverter(project) {
    private val blockComment = "/*"

    override fun convert(customConfigs: Set<String>) {
        LogUtil.info("need convert to source: $lineSeparator${customConfigs.joinToString(lineSeparator)}")
        val affectedList = mutableSetOf<String>()
        val changeList = mutableSetOf<Pair<Int, String>>()
        val other = others?.values?.flatMap { set ->
            set.map { DependencyUtil.dependencyConfig.customToLocalPath(it) }
        }
        project.rootProject.subprojects.forEach { sub ->
            sub.configurations.all { c ->
                if (c.name in DependencyUtil.dependencyConfig.configTypes) {
                    c.dependencies.all { module ->
                        if (module is ModuleDependency) {
                            if (other?.contains(
                                    DependencyUtil.dependencyConfig.identifierToLocalPath(
                                        DependencyUtil.getMavenIdentifier(DependencyInfo(module))
                                    )
                                ) != true
                            ) {
                                customConfigs.forEach { config ->
                                    DependencyUtil.getAllAffectedModules(DependencyInfo(module), config, affectedList)
                                }
                            }
                        }
                    }
                }
            }
        }
        // 如果本地项目被注释则修改settings.gradle
        val gradle = File(project.rootProject.rootDir.absolutePath, Settings.DEFAULT_SETTINGS_FILE)
        val projects = DependencyUtil.readStringFromFile(gradle).split(',').toMutableList()
        project.rootProject.allprojects { pro ->
            pro.configurations.all { config ->
                config.resolutionStrategy.dependencySubstitution { substitution ->
                    affectedList.forEach { module ->
                        val localProject = DependencyUtil.dependencyConfig.identifierToLocalPath(module)
                        if (project.rootProject.findProject(localProject) == null) {
                            val found = projects.indexOfFirst {
                                it.contains(localProject)
                            }
                            if (found == -1) {
                                throw IllegalStateException("not found local project: $localProject")
                            }
                            val cur = projects[found].trim().split(lineSeparator)
                            var proStr = cur.last()
                            val len = cur.last().length
                            val trim = proStr.trim()
                            if (trim.startsWith(lineComment)) {
                                proStr = proStr.replaceFirst(lineComment, "")
                            } else if (trim.startsWith(blockComment)) {
                                proStr = proStr.replaceFirst(blockComment, "") + blockComment
                            }
                            if (proStr != cur.last()) {
                                changeList.add(
                                    Pair(
                                        found,
                                        projects[found].substring(
                                            0,
                                            projects[found].length - len
                                        ) + proStr
                                    )
                                )
                            }
                        }
                    }
                    // 将maven库依赖统一替换为源码依赖
                    affectedList.forEach { module ->
                        val localProject = DependencyUtil.dependencyConfig.identifierToLocalPath(module)
                        if (project.rootProject.findProject(localProject) == null && changeList.isNotEmpty()) {
                            return@forEach
                        }
                        if (config.state == Configuration.State.UNRESOLVED) {
                            substitution.substitute(
                                substitution.module(module)
                            ).with(
                                substitution.project(localProject)
                            )
                        }
                    }
                }
            }
        }
        project.gradle.buildFinished {
            if (changeList.isEmpty()) {
                if (affectedList.isNotEmpty()) {
                    LogUtil.info(
                        "converted to source ->$lineSeparator${
                            affectedList.joinToString(
                                lineSeparator
                            )
                        }"
                    )
                } else {
                    LogUtil.info("no convert to source")
                }
                return@buildFinished
            }
            changeList.forEach {
                projects[it.first] = it.second
            }
            DependencyUtil.writeStringToFile(
                gradle,
                projects.joinToString(",")
            )
            throw IllegalStateException("changed '${Settings.DEFAULT_SETTINGS_FILE}', please check it and sync manually")
        }
    }
}