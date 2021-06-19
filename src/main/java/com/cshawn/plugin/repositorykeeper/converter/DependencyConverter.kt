package com.cshawn.plugin.repositorykeeper.converter

import org.gradle.api.Project

/**
 * 依赖类型转换器
 * @author: C.Shawn
 * @date: 2021/5/17 10:10 PM
 */
abstract class DependencyConverter(
    protected val project: Project
) {
    protected val lineSeparator = "\n"
    protected val lineComment = "//"
    protected var others: Map<String, MutableSet<String>>? = null

    fun setOthers(others: Map<String, MutableSet<String>>): DependencyConverter {
        this.others = others
        return this
    }

    /**
     * 将当前依赖类型转换成目标依赖类型
     */
    abstract fun convert(customConfigs: Set<String>)
}