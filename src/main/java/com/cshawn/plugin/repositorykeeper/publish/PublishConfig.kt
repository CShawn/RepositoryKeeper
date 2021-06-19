package com.cshawn.plugin.repositorykeeper.publish

import java.net.URI

/**
 *
 * @author: C.Shawn
 * @date: 2021/5/27 11:07 AM
 */
open class PublishConfig {
    var needPublish: (String) -> Boolean = { false }
    var authorisePublisher: (String) -> Boolean = { true }
    var upgradeAffectedModule: (String) -> String = { it }
    var localPathToMavenIdentifier: (String) -> String = { it }
    var mavenLocalPath: URI = URI("")
    var mavenReleaseUrl: URI = URI("")
    var mavenSnapshotUrl: URI = URI("")
    var username: String = ""
    var password: String = ""
    var afterPublish: (Set<String>) -> Unit = {}
}