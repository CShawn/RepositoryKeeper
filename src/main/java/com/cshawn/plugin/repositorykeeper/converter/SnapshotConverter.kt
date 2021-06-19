package com.cshawn.plugin.repositorykeeper.converter

import com.cshawn.plugin.repositorykeeper.DependencyUtil
import com.cshawn.plugin.repositorykeeper.LogUtil
import com.cshawn.plugin.repositorykeeper.request.DependencyInfo
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleDependency

/**
 * 将当前依赖类型转换成snapshot依赖类型
 * 将直接依赖target-source的source工程中，先改gradle文件中的依赖方式改为maven-release依赖, 再动态替换release为snapshot
 * 将所有直接间接依赖target-maven的maven库，动态替换为snapshot依赖
 * @author: C.Shawn
 * @date: 2021/5/17 10:19 PM
 */
class SnapshotConverter(
    project: Project
) : ReleaseConverter(project) {
    override fun convert(customConfigs: Set<String>) {
        LogUtil.info("need convert to snapshot: $lineSeparator${customConfigs.joinToString(lineSeparator)}")
        changeLocalToMaven(customConfigs)
        val affectedList = mutableSetOf<String>()
        project.rootProject.subprojects.forEach { sub ->
            sub.configurations.all { c ->
                if (c.name in DependencyUtil.dependencyConfig.configTypes) {
                    c.dependencies.all { module ->
                        if (module is ModuleDependency) {
                            customConfigs.forEach { config ->
                                DependencyUtil.getAllAffectedModules(DependencyInfo(module), config, affectedList)
                            }
                        }
                    }
                }
                c.resolutionStrategy.dependencySubstitution { substitution ->
                    affectedList.forEach { module ->
                        if (c.state == Configuration.State.UNRESOLVED) {
                            substitution.substitute(
                                substitution.module(module)
                            ).with(
                                substitution.module("$module-${DependencyType.DEPENDENCY_SNAPSHOT}")
                            )
                        }
                    }
                }
            }
        }
        project.gradle.buildFinished {
            if (affectedList.isNotEmpty()) {
                LogUtil.info(
                    "converted to snapshot ->$lineSeparator${
                        affectedList.joinToString(
                            lineSeparator
                        )
                    }"
                )
            } else {
                LogUtil.info("no convert to snapshot")
            }
        }
    }
}