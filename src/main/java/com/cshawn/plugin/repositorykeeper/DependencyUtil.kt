package com.cshawn.plugin.repositorykeeper

import groovy.util.Node
import groovy.util.NodeList
import groovy.util.XmlParser
import groovy.xml.QName
import com.cshawn.plugin.repositorykeeper.converter.DependencyConfig
import com.cshawn.plugin.repositorykeeper.converter.DependencyType
import com.cshawn.plugin.repositorykeeper.publish.PublishConfig
import com.cshawn.plugin.repositorykeeper.request.DependencyInfo
import com.cshawn.plugin.repositorykeeper.request.PomInfo
import com.cshawn.plugin.repositorykeeper.request.RequestUtil
import org.gradle.api.Project
import java.io.*
import java.lang.IllegalStateException
import java.util.*
import kotlin.collections.LinkedHashMap

/**
 *
 * @author: C.Shawn
 * @date: 2021/5/17 10:58 PM
 */
object DependencyUtil {
    const val aar = "aar"
    const val jar = "jar"
    const val pom = "pom"
    const val sha1 = "sha1"
    const val cachePublishingProject = "publishingProject"
    lateinit var root: Project
    lateinit var dependencyConfig: DependencyConfig
    lateinit var publishConfig: PublishConfig
    private val dependencyCache : MutableMap<String, MutableMap<String, Boolean>> by lazy {
        mutableMapOf<String, MutableMap<String, Boolean>>()
    }
    private val cacheFile: File by lazy {
        val dir = File(dependencyConfig.cachePath)
        if (!dir.exists()) {
            dir.mkdirs()
            LogUtil.info("create dir: ${dir.absolutePath}")
        }

        val file = File("${dependencyConfig.cachePath}${File.separator}cache.properties")
        if (!file.exists()) {
            file.createNewFile()
            LogUtil.info("created ${file.absolutePath}")
        }
        file
    }
    private val cacheDirInfo: Properties by lazy {
        Properties().apply {
            val inputStream = FileInputStream(cacheFile)
            load(inputStream)
            inputStream.close()
        }
    }

    private fun Properties.save() {
        val out = FileOutputStream(cacheFile)
        store(out, null)
        out.close()
    }

    fun getCache(key: String, value: String = ""): String = cacheDirInfo.getProperty(key, value)

    fun saveCache(key: String, value: String) {
        cacheDirInfo.setProperty(key, value)
        cacheDirInfo.save()
    }

    fun saveCache() {
        cacheDirInfo.save()
    }

    fun clearCache(key: String) = cacheDirInfo.remove(key)

    fun getSha1InCache(identifier: String, extension: String): String = cacheDirInfo.getProperty("$identifier.$extension", "")

    fun saveSha1InCache(identifier: String, extension: String, value: String) {
        cacheDirInfo.setProperty("$identifier.$extension", value)
        cacheDirInfo.save()
    }

    fun getFileIdentifierWithExtension(fileType: String) = "$fileType.$sha1"

    /**
     * 从服务端获取依赖当前组件的组件
     * @param target 配置文件中的工程标识
     */
    fun getAllAffectedModules(module: DependencyInfo, target: String, list: MutableSet<String>): Boolean {
        val identifier = getMavenIdentifier(module)
        if (!dependencyConfig.targetMavenDependency(identifier)) {
            return false
        }
        if (list.contains(identifier)) {
            return true
        }
        if (dependencyCache[target]?.get(identifier) == false) {
            return false
        }
        LogUtil.info("substitute, check: $identifier")
        if (dependencyCache[target] == null) {
            dependencyCache[target] = mutableMapOf()
        }
        if (dependencyConfig.customMatchMaven(target, identifier)) {
            list.add(identifier)
            dependencyCache[target]?.put(identifier, true)
            return true
        }

        val dir = File(getLibraryCachePath(module.groupId, module.artifactId, module.version))
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val pomSha1 = getSha1InCache(identifier, pom).takeIf {
            it.isNotEmpty() && !isSnapshot(module)
        } ?: RequestUtil.getRequestService().getContent(
                module.groupId.replace('.', '/'),
                module.artifactId,
                module.version,
                "$pom.$sha1"
            ).execute().takeIf {
                it.isSuccessful
            }?.body()?.string()?.also {
            saveSha1InCache(identifier, pom, it)
        } ?: throw IllegalStateException("substitute, get pom.sha1 from server failed: $identifier")
        LogUtil.info("substitute, pom sha1: $pomSha1")

        val namePrefix = "${module.artifactId}-${module.version}"
        val file = File(dir, "$pomSha1${File.separator}$namePrefix.$pom")

        fun parsePom(): Boolean {
            var found = false
            getPom(file.absolutePath)?.dependencies?.forEach { maven ->
                val mavenIdentifier = getMavenIdentifier(maven)
                if (list.contains(mavenIdentifier)) {
                    found = true
                    return@forEach
                }
                if (dependencyConfig.customMatchMaven(target, mavenIdentifier)) {
                    found = true
                    LogUtil.info("substitute, affected: $identifier -> $target")
                    list.add(mavenIdentifier)
                    list.add(identifier)
                    dependencyCache[target]?.put(mavenIdentifier, true)
                    dependencyCache[target]?.put(identifier, true)
                } else {
                    if (getAllAffectedModules(maven, target, list)) {
                        found = true
                        list.add(mavenIdentifier)
                        list.add(identifier)
                        dependencyCache[target]?.put(mavenIdentifier, true)
                        dependencyCache[target]?.put(identifier, true)
                        LogUtil.info("substitute, affected: $identifier -> $target")
                    } else {
                        dependencyCache[target]?.put(mavenIdentifier, false)
                    }
                }
            }
            return found.also {
                dependencyCache[target]?.put(identifier, it)
            }
        }

        if (!isSnapshot(module) && file.exists()) {
            return parsePom()
        }
        LogUtil.info("substitute, download pom for $identifier")
        return RequestUtil.getRequestService().getFile(
            module.groupId.replace('.', '/'),
            module.artifactId,
            module.version,
            pom
        ).execute().takeIf {
            it.isSuccessful
        }?.body()?.run {
            writeStringToFile(file, string())
            parsePom()
        } ?: throw IllegalStateException("substitute, download pom failed, $identifier")
    }

    private fun isSnapshot(module: DependencyInfo): Boolean {
        return module.version.endsWith(DependencyType.DEPENDENCY_SNAPSHOT, true)
    }

    private fun getPom(path: String): PomInfo? {
        val file = File(path)
        if (!file.exists()) {
            return null
        }
        LogUtil.info("parse pom: $path")
        try {
            val xmlParser = XmlParser()
            val node = xmlParser.parse(file) ?: return null
            val pomInfo = PomInfo()
            node.children().forEach { property ->
                val pro = property as Node
                val list = pro.value() as? NodeList
                when ((pro.name() as QName).localPart) {
                    "groupId" -> pomInfo.groupId = list?.get(0)?.toString() ?: ""
                    "artifactId" -> pomInfo.artifactId = list?.get(0)?.toString() ?: ""
                    "version" -> pomInfo.version = list?.get(0)?.toString() ?: ""
                    "dependencies" -> pomInfo.dependencies = list?.map { infos ->
                        DependencyInfo().apply {
                            val item = infos as Node
                            (item.value() as? NodeList)?.forEach { attr ->
                                val attrNode = attr as Node
                                val value = (attrNode.value() as? NodeList)?.get(0)?.toString() ?: ""
                                when ((attrNode.name() as QName).localPart) {
                                    "groupId" -> groupId = value
                                    "artifactId" -> artifactId = value
                                    "version" -> version = value
                                }
                            }
                        }
                    }
                }
            }
            return pomInfo
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * @param target maven标识
     */
    fun getAllAffectedModules(module: DependencyInfo, target: String, list: LinkedHashMap<String, String>): Boolean {
        val identifier = getMavenIdentifier(module)
        if (identifier.substringBeforeLast(':') == target.substringBeforeLast(':')) {
            dependencyCache[target]?.put(identifier, true)
            return true
        }
        if (!dependencyConfig.targetMavenDependency(identifier)) {
            return false
        }
        if (list.containsKey(identifier)) {
            return true
        }
        if (dependencyCache[target]?.get(identifier) == false) {
            return false
        }
        LogUtil.info("publish, check: ${module.artifactId}")
        if (dependencyCache[target] == null) {
            dependencyCache[target] = mutableMapOf()
        }
        val normalVersion = module.version.substringBefore('-')
        val isSnapshot = isSnapshot(module)
        val snapshotVersion = if (isSnapshot) {
            getSnapshotVersion(module.groupId.replace('.', '/'), module.artifactId, normalVersion)
        } else ""
        if (isSnapshot) {
            LogUtil.info("publish, snapshot version: $snapshotVersion")
        } else {
            LogUtil.info("publish, version: ${module.version}")
        }

        val dir = File(getLibraryCachePath(module.groupId, module.artifactId, module.version))
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val pomKey = "$identifier.$pom"
        var pomSha1 = cacheDirInfo.getProperty(pomKey, "")
        if (pomSha1.isEmpty() || isSnapshot(module)) {
            LogUtil.info("publish, get pom.sha1 from server: $identifier")
            pomSha1 = (if (isSnapshot) RequestUtil.getRequestService().getSnapshotContent(
                module.groupId.replace('.', '/'),
                module.artifactId,
                normalVersion,
                snapshotVersion,
                "$pom.$sha1"
            ) else RequestUtil.getRequestService().getContent(
                module.groupId.replace('.', '/'),
                module.artifactId,
                normalVersion,
                "$pom.$sha1"
            )).execute().takeIf {
                it.isSuccessful
            }?.body()?.string() ?: throw IllegalStateException("publish, get pom.sha1 from server failed: $identifier")
            cacheDirInfo.setProperty(pomKey, pomSha1)
            cacheDirInfo.save()
        }
        LogUtil.info("publish, pom sha1: $pomSha1")

        val namePrefix = "${module.artifactId}-${module.version}"
        val file = File(dir, "$pomSha1${File.separator}$namePrefix.$pom")
        fun parsePom(): Boolean {
            var found = false
            LogUtil.info(file.absolutePath)
            getPom(file.absolutePath)?.dependencies?.forEach { maven ->
                val mavenIdentifier = getMavenIdentifier(maven)
                if (list.containsKey(mavenIdentifier)) {
                    found = true
                    return@forEach
                }
                if (target.substringBeforeLast(':') == mavenIdentifier.substringBeforeLast(':')) {
                    found = true
                    LogUtil.info("affected: $identifier -> $target")
                    list[identifier] = target
                    dependencyCache[target]?.put(identifier, true)
                } else {
                    if (getAllAffectedModules(maven, target, list)) {
                        found = true
                        list[identifier] = mavenIdentifier
                        dependencyCache[target]?.put(mavenIdentifier, true)
                        dependencyCache[target]?.put(identifier, true)
                        LogUtil.info("affected: $identifier -> $target")
                    } else {
                        dependencyCache[target]?.put(mavenIdentifier, false)
                    }
                }
            }
            return found.also {
                dependencyCache[target]?.put(identifier, it)
            }
        }

        if (file.exists()) {
            return parsePom()
        }
        LogUtil.info("publish, download pom from $identifier")
        return (if (isSnapshot) RequestUtil.getRequestService().getSnapshotFile(
            module.groupId.replace('.', '/'),
            module.artifactId,
            normalVersion,
            snapshotVersion,
            pom
        ) else RequestUtil.getRequestService().getFile(
            module.groupId.replace('.', '/'),
            module.artifactId,
            normalVersion,
            pom
        )).execute().takeIf {
            it.isSuccessful
        }?.body()?.run {
            writeStringToFile(file, string())
            parsePom()
        } ?: throw IllegalStateException("publish, download pom for $identifier failed")
    }


    fun getSnapshotVersion(group: String, name: String, version: String): String {
        val metaData = RequestUtil.getRequestService()
            .getSnapshotMetaData(group, name, version).execute().takeIf {
                it.isSuccessful
            }?.body()?.string() ?: throw IllegalStateException("get metadata failed: $group:$name:$version")
        val xmlParser = XmlParser()
        val node = xmlParser.parseText(metaData)
        return (((((((node.get("versioning") as NodeList)
            .getAt("snapshotVersions")[0] as Node)
            .value() as NodeList)[0] as Node)
            .get("value") as NodeList)[0] as Node)
            .value() as NodeList)[0]
            .toString()
    }

    fun getMavenIdentifier(module: DependencyInfo) = "${module.groupId}:${module.artifactId}:${module.version}"

    fun readStringFromFile(file: File): String {
        if (!file.exists() || file.isDirectory) {
            return ""
        }
        var fis: FileInputStream? = null
        try {
            fis = FileInputStream(file)
            val length = fis.available()
            val buffer = ByteArray(length)
            fis.read(buffer)
            return String(buffer)
        } catch (e: Exception) {
            e.printStackTrace()
            LogUtil.info("read failed, ${e.message}")
        } finally {
            try {
                fis?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return ""
    }

    fun writeStringToFile(file: File, str: String, isAppend: Boolean = false): Boolean {
        if (file.isDirectory) {
            return false
        }
        if (!file.exists()) {
            file.parentFile.mkdirs()
            file.createNewFile()
        }
        var res = false
        var fos: FileOutputStream? = null
        try {
            fos = FileOutputStream(file, isAppend)
            fos.write(str.toByteArray())
            fos.flush()
            res = true
        } catch (e: Exception) {
            LogUtil.info("write failed, ${e.message}")
        } finally {
            try {
                fos?.flush()
                fos?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return res
    }

    fun getLibraryCachePath(groupId: String, artifactId: String, version: String): String {
        return "${root.gradle.gradleUserHomeDir}${File.separator}caches${
            File.separator}modules-2${File.separator}files-2.1${
            File.separator}$groupId${File.separator}$artifactId${
            File.separator}$version${File.separator}"
    }
}