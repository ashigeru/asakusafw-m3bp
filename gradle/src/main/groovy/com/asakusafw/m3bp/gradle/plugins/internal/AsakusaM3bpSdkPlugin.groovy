/*
 * Copyright 2011-2019 Asakusa Framework Team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.asakusafw.m3bp.gradle.plugins.internal

import org.gradle.api.Plugin
import org.gradle.api.Project

import com.asakusafw.gradle.plugins.AsakusafwCompilerExtension
import com.asakusafw.gradle.plugins.AsakusafwPluginConvention
import com.asakusafw.gradle.plugins.internal.AsakusaSdkPlugin
import com.asakusafw.gradle.plugins.internal.PluginUtils
import com.asakusafw.gradle.tasks.AsakusaCompileTask
import com.asakusafw.gradle.tasks.internal.ResolutionUtils

/**
 * A Gradle sub plug-in for Asakusa on M3BP SDK.
 * @since 0.1.0
 * @version 0.3.0
 * @see AsakusaM3bpSdkBasePlugin
 */
class AsakusaM3bpSdkPlugin implements Plugin<Project> {

    /**
     * The compile batch application task name.
     */
    public static final String TASK_COMPILE = 'm3bpCompileBatchapps'

    private static final Map<String, String> REDIRECT = [
            'com.asakusafw.runtime.core.BatchContext' : 'com.asakusafw.m3bp.custom.M3bpBatchContext',
            'com.asakusafw.runtime.core.Report' : 'com.asakusafw.bridge.api.Report',
            'com.asakusafw.runtime.directio.api.DirectIo' : 'com.asakusafw.bridge.directio.api.DirectIo',
    ]

    private Project project

    private AsakusafwCompilerExtension extension

    @Override
    void apply(Project project) {
        this.project = project

        project.apply plugin: AsakusaM3bpSdkBasePlugin
        this.extension = AsakusaSdkPlugin.get(project).extensions.create('m3bp', AsakusafwCompilerExtension)

        configureExtension()
        defineTasks()
    }

    private void configureExtension() {
        AsakusaM3bpBaseExtension base = AsakusaM3bpBasePlugin.get(project)
        AsakusafwPluginConvention sdk = AsakusaSdkPlugin.get(project)
        extension.conventionMapping.with {
            outputDirectory = { project.relativePath(new File(project.buildDir, 'm3bp-batchapps')) }
            batchIdPrefix = { (String) 'm3bp.' }
            failOnError = { true }
        }
        REDIRECT.each { k, v ->
            extension.compilerProperties.put((String) "redirector.rule.${k}", v)
        }
        extension.compilerProperties.put('javac.version', { sdk.javac.sourceCompatibility.toString() })
        PluginUtils.injectVersionProperty(extension, { base.featureVersion })
    }

    private void defineTasks() {
        AsakusafwPluginConvention sdk = AsakusaSdkPlugin.get(project)
        project.tasks.create(TASK_COMPILE, AsakusaCompileTask) { AsakusaCompileTask task ->
            task.group AsakusaSdkPlugin.ASAKUSAFW_BUILD_GROUP
            task.description 'Compiles Asakusa DSL source files for M3BP environment'
            task.dependsOn 'classes'

            task.compilerName = 'Asakusa DSL compiler for M3BP'

            task.launcherClasspath << { project.configurations.asakusaToolLauncher }

            task.toolClasspath << { project.configurations.asakusaM3bpCompiler }
            task.toolClasspath << { project.sourceSets.main.compileClasspath - project.configurations.compile }

            task.explore << { PluginUtils.getClassesDirs(project, project.sourceSets.main.output).findAll { it.exists() } }
            task.embed << { [project.sourceSets.main.output.resourcesDir].findAll { it.exists() } }
            task.attach << { project.configurations.embedded }

            task.include << { extension.include }
            task.exclude << { extension.exclude }

            task.clean = true

            task.conventionMapping.with {
                maxHeapSize = { sdk.maxHeapSize }
                runtimeWorkingDirectory = { extension.runtimeWorkingDirectory }
                batchIdPrefix = { extension.batchIdPrefix }
                outputDirectory = { project.file(extension.outputDirectory) }
                failOnError = { extension.failOnError }
            }
            project.tasks.compileBatchapp.dependsOn task
            project.tasks.jarBatchapp.from { task.outputDirectory }
        }
        PluginUtils.afterEvaluate(project) {
            AsakusaCompileTask task = project.tasks.getByName(TASK_COMPILE)
            Map<String, String> map = [:]
            map.putAll(ResolutionUtils.resolveToStringMap(extension.compilerProperties))
            map.putAll(ResolutionUtils.resolveToStringMap(task.compilerProperties))
            task.compilerProperties = map

            if (sdk.logbackConf != null) {
                File f = project.file(sdk.logbackConf)
                task.systemProperties.put('logback.configurationFile', f.absolutePath)
            }
        }
    }
}
