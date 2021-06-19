package com.cshawn.plugin.repositorykeeper.publish

import com.android.build.gradle.LibraryExtension
import groovy.util.Node
import groovy.util.NodeList
import groovy.xml.QName
import com.cshawn.plugin.repositorykeeper.DependencyUtil
import com.cshawn.plugin.repositorykeeper.LogUtil
import com.cshawn.plugin.repositorykeeper.converter.DependencyType
import com.cshawn.plugin.repositorykeeper.request.DependencyInfo
import com.cshawn.plugin.repositorykeeper.request.RequestUtil
import org.gradle.api.*
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.internal.artifact.FileBasedMavenArtifact
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.jvm.tasks.Jar
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import kotlin.collections.LinkedHashMap

/**
 * 发布组件的插件
 * @author: C.Shawn
 * @date: 2021/5/27 11:00 AM
 */
class PublishPlugin : Plugin<Project> {
    private val unspecified = "unspecified"
    private val sourceClassifier = "sources"
    private val mavenLocal = "mavenLocal"
    private val uploadGroup = "upload"

    override fun apply(target: Project) {
        target.plugins.apply(MavenPublishPlugin::class.java)
        target.afterEvaluate { project ->
            val needPublish = DependencyUtil.publishConfig.needPublish(project.path)
            if (!needPublish) {
                return@afterEvaluate
            }
            val identifier = DependencyUtil.publishConfig.localPathToMavenIdentifier(project.path)
            val arr = identifier.split(":")
            if (arr.size != 3 || arr.any { it.isEmpty() } || arr[2] == unspecified) {
                throw IllegalArgumentException("invalid identifier: $identifier")
            }
            LogUtil.info("add publish task for: $identifier")

            val sourceJar = project.tasks.create("makeSourceJar", Jar::class.java) {
                it.from(project.extensions.findByType(LibraryExtension::class.java)?.sourceSets?.findByName("main")?.java?.srcDirs)
                it.archiveClassifier.set("sources")
            }
            project.extensions.findByType(PublishingExtension::class.java)?.also { publishing ->
                publishing.repositories { repositories ->
                    repositories.mavenLocal {
                        it.url = DependencyUtil.publishConfig.mavenLocalPath
                    }
                    repositories.maven { maven ->
                        maven.name = DependencyType.DEPENDENCY_RELEASE
                        maven.url = DependencyUtil.publishConfig.mavenReleaseUrl
                        maven.credentials {
                            it.username = DependencyUtil.publishConfig.username
                            it.password = DependencyUtil.publishConfig.password
                        }
                    }
                    repositories.maven { maven ->
                        maven.name = DependencyType.DEPENDENCY_SNAPSHOT
                        maven.url = DependencyUtil.publishConfig.mavenSnapshotUrl
                        maven.credentials {
                            it.username = DependencyUtil.publishConfig.username
                            it.password = DependencyUtil.publishConfig.password
                        }
                    }
                }
                project.components.all { component ->
                    if (component.name == "release") {
                        publishing.publications.create(DependencyType.DEPENDENCY_RELEASE, MavenPublication::class.java) { publication ->
                            publication.from(component)
                            publication.artifact(sourceJar)
                            publication.groupId = arr[0]
                            publication.artifactId = arr[1]
                            publication.version = arr[2]
                            publication.pom.withXml {
                                configPublisher(project, it)
                            }
                        }
                        publishing.publications.create(DependencyType.DEPENDENCY_SNAPSHOT, MavenPublication::class.java) { publication ->
                            publication.from(component)
                            publication.artifact(sourceJar)
                            publication.groupId = arr[0]
                            publication.artifactId = arr[1]
                            publication.version = "${arr[2]}-${DependencyType.DEPENDENCY_SNAPSHOT.toUpperCase(Locale.getDefault())}"
                            publication.pom.withXml { xml ->
                                // 修改为Snapshot依赖
                                xml.asNode().children().forEach { property ->
                                    val pro = property as Node
                                    val list = pro.value() as? NodeList
                                    when ((pro.name() as QName).localPart) {
                                        "dependencies" -> {
                                            list?.forEach { dependency ->
                                                val item = dependency as Node
                                                var group = ""
                                                var name = ""
                                                (item.value() as? NodeList)?.forEach { attr ->
                                                    val attrNode = attr as Node
                                                    val node = (attrNode.value() as? NodeList)
                                                    val value = node?.get(0)?.toString() ?: ""
                                                    when ((attrNode.name() as QName).localPart) {
                                                        "groupId" -> group = value
                                                        "artifactId" -> name = value
                                                        "version" -> {
                                                            if (DependencyUtil.dependencyConfig.targetMavenDependency("$group:$name:$value")) {
                                                                node?.set(0, "$value-${DependencyType.DEPENDENCY_SNAPSHOT
                                                                    .toUpperCase(Locale.getDefault())}")
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                configPublisher(project, xml)
                            }
                        }

                        // 在执行过程中，为受影响的工程创建发布任务
                        if (DependencyUtil.getCache(DependencyUtil.cachePublishingProject).let {
                            it.isNotEmpty() && it == project.path
                        }) {
                            LogUtil.info("cached publishing project")
                            checkAndCreateAffectedPublication(project, LinkedHashMap(), arr[2])
                        }

                        // 创建可执行的发布任务
                        createUploadTask(
                            "uploadSnapshotToLocal",
                            project,
                            DependencyType.DEPENDENCY_SNAPSHOT,
                            mavenLocal,
                            arr[2]
                        )
                        createUploadTask(
                            "uploadReleaseToLocal",
                            project,
                            DependencyType.DEPENDENCY_RELEASE,
                            mavenLocal,
                            arr[2]
                        )
                        createUploadTask(
                            "uploadSnapshotToMaven",
                            project,
                            DependencyType.DEPENDENCY_SNAPSHOT,
                            DependencyType.DEPENDENCY_SNAPSHOT,
                            arr[2]
                        ).doFirst {
                            // 未上传此版本的release库时，不可上传snapshot版本
                            if (RequestUtil.getRequestService()
                                    .getMetaData(arr[0].replace('.', '/'), arr[1])
                                    .execute().isSuccessful
                            ) {
                                RequestUtil.getRequestService().getContent(
                                    arr[0].replace('.', '/'),
                                    arr[1],
                                    arr[2],
                                    DependencyUtil.pom
                                ).execute().takeIf {
                                    it.isSuccessful
                                } ?: throw IllegalStateException("cannot publish a snapshot to not existed release version: ${arr[2]}")
                            }
                        }
                        createUploadTask(
                            "uploadReleaseToMaven",
                            project,
                            DependencyType.DEPENDENCY_RELEASE,
                            DependencyType.DEPENDENCY_RELEASE,
                            arr[2]
                        ).doFirst {
                            RequestUtil.getRequestService()
                                .getContent(arr[0].replace('.', '/'), arr[1], arr[2], DependencyUtil.pom).execute()
                                .takeUnless {
                                    it.isSuccessful
                                } ?: throw IllegalStateException("already existed on maven: $identifier")
                        }
                    }
                }
            }
        }
    }

    private fun createFileBasedPublication(
        project: Project,
        publishing: PublishingExtension,
        publicationName: String,
        module: String,
        publishedVersion: String
    ) {
        val isSnapshot = publicationName.endsWith(DependencyType.DEPENDENCY_SNAPSHOT, true)
        LogUtil.info("create: $publicationName")
        publishing.publications.create(
            publicationName,
            MavenPublication::class.java
        ) { publication ->
            val identifierArr = module.split(":")
            val groupPath = identifierArr[0].replace('.', '/')
            val snapshotVersion = if (isSnapshot) {
                DependencyUtil.getSnapshotVersion(groupPath, identifierArr[1], identifierArr[2])
            } else ""
            if (isSnapshot) {
                LogUtil.info("snapshot version: $snapshotVersion")
            } else {
                LogUtil.info("version: ${identifierArr[2]}")
            }
            val path = DependencyUtil.getLibraryCachePath(identifierArr[0], identifierArr[1], identifierArr[2])
            val namePrefix = "${identifierArr[1]}-${identifierArr[2]}"
            publication.groupId = identifierArr[0]
            publication.artifactId = identifierArr[1]
            val upgraded = DependencyUtil.publishConfig.upgradeAffectedModule(module).split(":")[2]
            val upgradedVersion = if (isSnapshot) "$upgraded-${
                DependencyType.DEPENDENCY_SNAPSHOT.toUpperCase(Locale.getDefault())
            }" else upgraded
            publication.version = upgradedVersion
            val aarSha1 = DependencyUtil.getSha1InCache(module, DependencyUtil.aar).takeIf {
                it.isNotEmpty()
            } ?: RequestUtil.getRequestService().getContent(
                groupPath,
                identifierArr[1],
                identifierArr[2],
                DependencyUtil.getFileIdentifierWithExtension(DependencyUtil.aar)
            ).execute().takeIf {
                it.isSuccessful
            }?.body()?.string()?.also {
                DependencyUtil.saveSha1InCache(module, DependencyUtil.aar, it)
            } ?: throw IllegalStateException("get ${DependencyUtil.aar}.${DependencyUtil.sha1} failed: $module")
            LogUtil.info("${DependencyUtil.aar}.sha1: $aarSha1")
            val aarFile = File("$path$aarSha1${File.separator}$namePrefix.${DependencyUtil.aar}")

            val pomSha1 = DependencyUtil.getSha1InCache(module, DependencyUtil.pom).takeIf {
                it.isNotEmpty()
            } ?: RequestUtil.getRequestService().getContent(
                groupPath,
                identifierArr[1],
                identifierArr[2],
                DependencyUtil.getFileIdentifierWithExtension(DependencyUtil.pom)
            ).execute().takeIf {
                it.isSuccessful
            }?.body()?.string()?.also {
                DependencyUtil.saveSha1InCache(module, DependencyUtil.pom, it)
            } ?: throw IllegalStateException("get ${DependencyUtil.pom}.${DependencyUtil.sha1} failed: $module")
            LogUtil.info("${DependencyUtil.pom}.${DependencyUtil.sha1}: $pomSha1")

            val sourceSha1 = DependencyUtil.getSha1InCache(module, DependencyUtil.jar).takeIf {
                it.isNotEmpty()
            } ?: RequestUtil.getRequestService().getContent(
                groupPath,
                identifierArr[1],
                identifierArr[2],
                sourceClassifier,
                DependencyUtil.getFileIdentifierWithExtension(DependencyUtil.jar)
            ).execute().takeIf {
                it.isSuccessful
            }?.body()?.string()?.also {
                DependencyUtil.saveSha1InCache(module, DependencyUtil.jar, it)
            } ?: throw IllegalStateException("get sourceJar.${DependencyUtil.sha1} failed: $module")
            LogUtil.info("source.${DependencyUtil.sha1}: $pomSha1")

            publication.setArtifacts(listOf(
                aarFile,
                object : FileBasedMavenArtifact(
                    File("$path$sourceSha1${File.separator}$namePrefix-$sourceClassifier.${DependencyUtil.jar}")
                ) {
                    override fun getDefaultClassifier(): String {
                        return sourceClassifier
                    }
                }
            ))
            publication.pom.withXml { xml ->
                xml.asString().apply {
                    setLength(0)
                    append(
                        DependencyUtil.readStringFromFile(
                            File("$path$pomSha1${File.separator}$namePrefix.${DependencyUtil.pom}")
                        )
                    )
                }
                // 修改版本号
                xml.asNode().children().forEach { property ->
                    val pro = property as Node
                    val list = pro.value() as? NodeList
                    when ((pro.name() as QName).localPart) {
                        "version" -> list?.set(0, upgradedVersion)
                        "dependencies" -> {
                            list?.forEach { dependency ->
                                val item = dependency as Node
                                var group = ""
                                var name = ""
                                (item.value() as? NodeList)?.forEach { attr ->
                                    val attrNode = attr as Node
                                    val node = (attrNode.value() as? NodeList)
                                    val value = node?.get(0)?.toString() ?: ""
                                    when ((attrNode.name() as QName).localPart) {
                                        "groupId" -> group = value
                                        "artifactId" -> name = value
                                        "version" -> {
                                            if (DependencyUtil.dependencyConfig.targetMavenDependency("$group:$name:$value")) {
                                                var changeVersion = if (project.name == name) publishedVersion else value
                                                if (isSnapshot) {
                                                    changeVersion = "$changeVersion-${DependencyType.
                                                    DEPENDENCY_SNAPSHOT.toUpperCase(Locale.getDefault())}"
                                                }
                                                node?.set(0, changeVersion)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                configPublisher(project, xml)
            }
        }
    }

    private fun getPublishTaskName(taskName: String, repositoryName: String): String {
        return "publish${taskName.upperCaseFirstChar()}PublicationTo${repositoryName.upperCaseFirstChar()}Repository"
    }

    private fun createAffectedPublication(
        affectedList: Map<String, String>,
        project: Project,
        type: String,
        publishedVersion: String
    ) {
        project.extensions.findByType(PublishingExtension::class.java)?.also { publishing ->
            val projectName = DependencyUtil.publishConfig.localPathToMavenIdentifier(project.path)
                .substringBeforeLast(":")

            affectedList.forEach { (module, base) ->
                val name = module.substringAfter(':').substringBefore(':')
                val publicationName = "$name${type.upperCaseFirstChar()}"
                createFileBasedPublication(project, publishing, publicationName, module, publishedVersion)
                arrayOf(mavenLocal, type).forEach { repositoryName ->
                    project.tasks.findByName(
                        getPublishTaskName(
                            publicationName,
                            repositoryName
                        )
                    )?.dependsOn(
                        if (base.substringBeforeLast(":") == projectName) project.tasks.findByName(
                            getPublishTaskName(type, repositoryName)
                        ) else getPublishTaskName(
                            "${base.substringAfter(':')
                                .substringBefore(':')}${type.upperCaseFirstChar()}",
                            repositoryName
                        )
                    )
                }
            }
        }
    }

    private fun getAffectedTasks(
        project: Project,
        affectedList: Map<String, String>,
        type: String,
        repositoryName: String
    ): Set<Task> {
        val createdTasks = mutableSetOf<Task>()
        if (type == DependencyType.DEPENDENCY_RELEASE) {
            affectedList.takeIf { it.isNotEmpty() }?.mapNotNull {
                project.tasks.findByName(
                    getPublishTaskName(
                        it.key.substringAfter(':')
                            .substringBefore(':') + DependencyType.DEPENDENCY_RELEASE.upperCaseFirstChar(),
                        repositoryName
                    )
                )
            }?.takeIf { it.isNotEmpty() }?.also { l ->
                LogUtil.info("affected release: ${l.joinToString { it.name }}")
                createdTasks.addAll(l)
            } ?: project.tasks.findByName(
                getPublishTaskName(
                    DependencyType.DEPENDENCY_RELEASE,
                    repositoryName
                )
            )?.also {
                LogUtil.info("no affected release, dependency on: ${it.name}")
                createdTasks.add(it)
            }

            // 上传release时会上传一份snapshot，用于下次修改代码时变动相关的依赖
            val snapshotRepository = if (repositoryName == mavenLocal) repositoryName
            else DependencyType.DEPENDENCY_SNAPSHOT
            affectedList.takeIf { it.isNotEmpty() }?.mapNotNull {
                project.tasks.findByName(
                    getPublishTaskName(
                        it.key.substringAfter(':')
                            .substringBefore(':') +
                                DependencyType.DEPENDENCY_SNAPSHOT.upperCaseFirstChar(),
                        snapshotRepository
                    )
                )
            }?.takeIf { it.isNotEmpty() }?.also { l ->
                LogUtil.info("affected snapshot: ${l.joinToString { it.name }}")
                createdTasks.addAll(l)
            } ?: project.tasks.findByName(
                getPublishTaskName(DependencyType.DEPENDENCY_SNAPSHOT, snapshotRepository)
            )?.also {
                LogUtil.info("no affected snapshot, dependency on: ${it.name}")
                createdTasks.add(it)
            }
        } else {
            project.tasks.findByName(
                getPublishTaskName(type, repositoryName)
            )?.also {
                createdTasks.add(it)
            }
        }
        return createdTasks
    }

    private fun createUploadTask(
        taskName: String,
        project: Project,
        type: String,
        repositoryName: String,
        publishedVersion: String
    ): Task {
        return project.task(taskName) { task ->
            task.group = uploadGroup
            val affectedList = LinkedHashMap<String, String>()
            task.doFirst {
                if (type == DependencyType.DEPENDENCY_RELEASE) {
                    checkAndCreateAffectedPublication(project, affectedList, publishedVersion)
                }
                val tasks = getAffectedTasks(project, affectedList, type, repositoryName)
                val commands = mutableListOf<String>()
                commands.addAll(getPlatformCmd())
                commands.add("${project.rootProject.rootDir}${File.separator}gradlew")
                commands.addAll(
                    tasks.map { it.path }
                )
                LogUtil.info("save publishing project ${project.path}")
                LogUtil.info(commands.joinToString())
                DependencyUtil.saveCache(DependencyUtil.cachePublishingProject, project.path)
                project.exec {  exe ->
                    exe.commandLine(*(commands.toTypedArray()))
                }
            }.doLast {
                if (type == DependencyType.DEPENDENCY_RELEASE) {
                    DependencyUtil.publishConfig.afterPublish(affectedList.keys)
                }
            }
        }
    }

    private fun configPublisher(project: Project, xml: XmlProvider) {
        val output = ByteArrayOutputStream()
        val commands = mutableListOf<String>()
        commands.addAll(getPlatformCmd())
        commands.add("git")
        commands.add("config")
        commands.add("user.name")
        project.exec {
            it.commandLine(*(commands.toTypedArray()))
            it.standardOutput = output
        }
        commands[commands.size - 1] = "user.email"
        project.exec {
            it.commandLine(*(commands.toTypedArray()))
            it.standardOutput = output
        }
        val result = output.toString()
        LogUtil.info("git user: $result")
        val str = result.split("\n")
        output.close()
        if (str.size != 3 || !DependencyUtil.publishConfig.authorisePublisher(str[0])) {
            throw IllegalStateException("illegal git user, no permission to publish")
        }
        val properties = xml.asNode().children().firstOrNull {
            ((it as Node).name() as? QName)?.localPart == "properties" ||
            it.name() as? String == "properties"
        } as? Node
        if (properties != null) {
            (properties.value() as NodeList).forEach { node ->
                when (((node as Node).name() as? QName)?.localPart?: node.name() as? String) {
                    "publisher" -> node.setValue(str[0])
                    "email" -> node.setValue(str[1])
                }
            }
        } else {
            xml.asNode().appendNode("properties").apply {
                appendNode("publisher", str[0])
                appendNode("email", str[1])
            }
        }
    }

    private fun String.upperCaseFirstChar() = "${this[0].toUpperCase()}${this.substring(1)}"

    private fun checkAndCreateAffectedPublication(
        project: Project,
        affectedList: LinkedHashMap<String, String>,
        publishedVersion: String
    ) {
        val identifier = DependencyUtil.publishConfig.localPathToMavenIdentifier(project.path)
        project.rootProject.subprojects.forEach { sub ->
            if (sub != project) {
                sub.configurations.all { c ->
                    if (c.name in DependencyUtil.dependencyConfig.configTypes) {
                        c.dependencies.all { module ->
                            if (module is ModuleDependency &&
                                DependencyUtil.dependencyConfig.identifierToLocalPath(
                                    DependencyUtil.getMavenIdentifier(
                                        DependencyInfo(
                                            module
                                        )
                                    )
                                ) != project.path
                            ) {
                                DependencyUtil.getAllAffectedModules(
                                    DependencyInfo(module),
                                    identifier,
                                    affectedList
                                )
                            }
                        }
                    }
                }
            }
        }
        createAffectedPublication(
            affectedList,
            project,
            DependencyType.DEPENDENCY_RELEASE,
            publishedVersion
        )
        createAffectedPublication(
            affectedList,
            project,
            DependencyType.DEPENDENCY_SNAPSHOT,
            publishedVersion
        )
    }

    private fun getPlatformCmd(): List<String> {
        val commands = mutableListOf<String>()
        if (System.getProperty("os.name").startsWith("win", true)) {
            commands.add("cmd")
            commands.add("/c")
        }
        return commands
    }
}