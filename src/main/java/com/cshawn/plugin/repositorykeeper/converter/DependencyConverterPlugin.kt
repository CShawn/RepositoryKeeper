package com.cshawn.plugin.repositorykeeper.converter

import com.cshawn.plugin.repositorykeeper.DependencyUtil
import com.cshawn.plugin.repositorykeeper.LogUtil
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File
import java.io.FileInputStream
import java.util.*

/**
 * 依赖方式转换插件
 * @author: C.Shawn
 * @date: 2021/5/17 10:36 PM
 */
class DependencyConverterPlugin : Plugin<Project> {
    // 配置项目依赖类型的配置文件
    private val configFile = "local.properties"

    override fun apply(project: Project) {
        LogUtil.info("dependency manage: " + project.name)
        project.afterEvaluate {
            if (DependencyUtil.dependencyConfig.mavenServer.isEmpty()) {
                throw IllegalArgumentException("maven server can not be empty")
            }
            if (DependencyUtil.dependencyConfig.cachePath.isEmpty()) {
                throw IllegalArgumentException("dependency config 'cachePath' can not be empty")
            }
            if (DependencyUtil.dependencyConfig.configTypes.isEmpty()) {
                throw IllegalArgumentException("dependency config 'configTypes' can not be empty")
            }
            if (DependencyUtil.dependencyConfig.dependencyKey.isEmpty()) {
                throw IllegalArgumentException("dependency config key can not be empty, 'dependency' for default")
            }
            if (DependencyUtil.dependencyConfig.dependencyKey != "dependency") {
                LogUtil.info("custom dependency key: ${DependencyUtil.dependencyConfig.dependencyKey}")
            }
            val customConfigs = getCustomDependencies(project, DependencyUtil.dependencyConfig.dependencyKey)
            if (customConfigs.isEmpty()) {
                LogUtil.info("no custom dependency config")
                return@afterEvaluate
            }
            customConfigs.forEach { entry ->
                LogUtil.info("custom dependency: ${entry.key} -> ${entry.value}")
                if (DependencyType.dependencyTypes.all { !it.equals(entry.value, true) }) {
                    throw IllegalArgumentException("invalid dependency type: ${entry.value}")
                }
            }

            // 按照依赖类型分组
            val map = mutableMapOf<String, MutableSet<String>>()
            customConfigs.forEach { entry ->
                map[entry.value]?.add(entry.key)?: mutableSetOf(entry.key).also {
                    map[entry.value] = it
                }
            }
            map.forEach { entry ->
                DependencyConverterFactory.getDependencyConverter(
                    entry.key.toLowerCase(Locale.getDefault()),
                    project
                ).setOthers(map.filterKeys { it != entry.key })
                    .convert(entry.value)
            }
        }
    }

    private fun getCustomDependencies(project: Project, dependencyKey: String): Map<String, String> {
        val properties = Properties()
        var inputStream: FileInputStream? = null
        try {
            inputStream = FileInputStream(File(project.rootDir.path, configFile))
            properties.load(inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            error("load $configFile failed: ${e.message}")
        } finally {
            inputStream?.close()
        }
        return properties.stringPropertyNames().filter { it.startsWith("$dependencyKey.") }.let { components ->
            mapOf(
                *(components.map { config ->
                    Pair(
                        config.substringAfter('.'),
                        properties.getProperty(config, DependencyType.DEPENDENCY_RELEASE)
                    )
                }.toTypedArray())
            )
        }
    }
}