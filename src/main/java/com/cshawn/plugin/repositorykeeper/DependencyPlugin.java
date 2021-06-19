package com.cshawn.plugin.repositorykeeper;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import com.cshawn.plugin.repositorykeeper.converter.DependencyConfig;
import com.cshawn.plugin.repositorykeeper.converter.DependencyConverterPlugin;
import com.cshawn.plugin.repositorykeeper.publish.PublishConfig;
import com.cshawn.plugin.repositorykeeper.publish.PublishPlugin;

/**
 * 依赖管理的插件
 * @author: C.Shawn
 * @date: 2021/5/20 10:14 AM
 */
public class DependencyPlugin implements Plugin<Project> {
    @Override
    public void apply(Project target) {
        DependencyUtil.INSTANCE.setRoot(target.getRootProject());
        DependencyUtil.INSTANCE.setDependencyConfig(
                target.getExtensions().create("dependencyConfig", DependencyConfig.class)
        );
        DependencyUtil.INSTANCE.setPublishConfig(
                target.getExtensions().create("publishConfig", PublishConfig.class)
        );
        target.getPlugins().apply(DependencyConverterPlugin.class);
        target.getGradle().buildFinished(buildResult -> {
            if (DependencyUtil.INSTANCE.getCache(DependencyUtil.cachePublishingProject, "").length() > 0) {
                DependencyUtil.INSTANCE.clearCache(DependencyUtil.cachePublishingProject);
                DependencyUtil.INSTANCE.saveCache();
            }
        });
        target.allprojects(project -> project.getPlugins().apply(PublishPlugin.class));
    }
}
