# RepositoryKeeper
该插件适用于组件化开发过程中管理组件依赖，主要功能有：
1. 发布组件及依赖当前组件的组件到`maven`私服。
2. 动态切换组件的依赖方式。

组件化开发过程中将各组件上传到`maven`私服，而需要修改组件时，会存在几个问题需要解决：
1. 开发过程中需要使用源码方式依赖，在修改完成后再次上传`maven`。
2. 当前组件被其他组件依赖时，发布新版本的组件包后，需要依赖当前组件的组件也相应升级新版本以依赖新版本的组件。
3. 开发协作过程中，可以使用快照的方式发布组件包以完成联调。

## 使用方式
### 应用插件
在项目工程的根`build.gradle`中：
```
apply plugin: 'com.cshawn.plugin.repositorykeeper'
allprojects {
    repositories {
        maven { url 'https://www.jitpack.io' }
    }
}
dependencies {
    classpath "com.cshawn.plugin.repositorykeeper:0.0.1"
}

dependencyConfig {
    mavenServer = "maven仓库的地址"
    cachePath = "缓存目录"
    // 需要遍历的依赖类型，一般情况下"implementation"与"api"已经足够
    configTypes = ["implementation", "api", "debugImplementation", "testImplementation", "androidTestImplementation"]
    targetMavenDependency {
        // 项目中依赖的库是否为自己开发的库
    }
    identifierToLocalPath {
        // 将maven仓库的标识转换为本地工程路径
    }
    localPathToMavenStr {
        // 将工程的本地路径转换为maven发布的标识'groupId:artifactId:version'的形式
    }
    customMatchMaven { custom, maven ->
        // 判断当前配置的custom工程是否与maven标识相匹配
    }
    customMatchLocal { custom, group, name ->
        // 判断当前配置的custom工程是否与本地的工程的group和name相匹配
    }
    customToLocalPath {
        // 将当前配置的custom工程转换为本地工程地址
    }
}

publishConfig {
    needPublish {
        // 是否需要为当前工程创建发布任务
    }
    localPathToMavenIdentifier {
        // 将工程的本地路径转换为maven发布的标识'groupId:artifactId:version'的形式
    }
    mavenLocalPath = "本地发布的maven地址"
    mavenReleaseUrl = "maven的正式仓库地址"
    mavenSnapshotUrl = "maven的快照仓库地址"
    username = "maven发布用户名"
    password = "maven发布认证密码"
    authorisePublisher {
        // 参数为发布者的git用户名，需要返回是否允许当前用户发布
    }
    upgradeAffectedModule {
        // 参数为受影响的组件identifier，需要返回升级版本后的identifier
    }
    afterPublish {
        // 在发布结束后回调受影响的组件，可以在此处修改组件的本地版本号
    }

// 需要指定更新SNAPSHOT周期时，增加以下代码
allprojects {
    // snapshot的更新周期，0为每次编译时更新
    configurations.all {
        resolutionStrategy {
            cacheChangingModulesFor 0, 'seconds'
        }
    }
}
```

### 发布到maven
在各组件的`gradle`任务中会产生一个`upload`的任务组：
- `uploadSnapshotToLocal`: 发布快照版本包到本地，只发布当前组件的快照版本且不做限制。
- `uploadReleaseToLocal`: 发布正式版本包到本地，发布当前组件的正式版本与快照版本及受影响的组件的正式、快照版本，不做限制。
- `uploadSnapshotToMaven`: 发布快照版本包到maven服务器，只发布当前组件的快照版本；当服务端不存在当前组件或存在当前组件的正式版本时，才可以发布快照版本。
- `uploadReleaseToLocal`: 发布正式版本包到maven服务器，发布当前组件的正式版本与快照版本及受影响的组件的正式、快照版本；当服务端不存在当前版本的组件时，才可以发布。

执行相应的命令可将对应的版本发布到指定的地址。

### 依赖切换
正式使用过程中，应使用`maven`依赖，只有在开发过程中才需要切换为源码依赖，为防止切换依赖后的误提交，将切换依赖的配置放在`local.properties`中：
```
    dependency.groupId.artifactId=source // 可选项为source, release, snapshot，不区分大小写
```

点击gradle同步按钮即可切换对应模块的依赖方式：
- `source`: 将当前以`maven`形式依赖的组件动态切换为源码依赖方式，同时切换依赖当前组件的所有组件为源码依赖方式。
- `release`: 如果当前组件是以源码方式依赖的，则在同步后修改每个依赖当前组件的工程下的`build.gradle`内容为`maven`依赖。需要再次同步，切换为`maven`依赖
- `snapshot`: 如果当前组件量是以源码方式依赖的，则第一步与切换为`release`相同，先修改`build.gradle`；第二次同步后，会动态切换为`snapshot`依赖。