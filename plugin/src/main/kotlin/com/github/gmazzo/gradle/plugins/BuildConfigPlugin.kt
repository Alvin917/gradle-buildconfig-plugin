package com.github.gmazzo.gradle.plugins

import com.github.gmazzo.gradle.plugins.internal.*
import com.github.gmazzo.gradle.plugins.internal.bindings.PluginBindings
import com.github.gmazzo.gradle.plugins.tasks.BuildConfigTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logging

class BuildConfigPlugin : Plugin<Project> {

    private val logger = Logging.getLogger(javaClass)

    override fun apply(project: Project) {
        val sourceSets = project.container(BuildConfigSourceSetInternal::class.java) { name ->
            DefaultBuildConfigSourceSet(
                DefaultBuildConfigClassSpec(name),
                project.container(BuildConfigClassSpecInternal::class.java, ::DefaultBuildConfigClassSpec)
            )
        }

        val defaultSS = sourceSets.create(DEFAULT_SOURCE_SET_NAME)

        val extension = project.extensions.create(
            BuildConfigExtension::class.java,
            "buildConfig",
            DefaultBuildConfigExtension::class.java,
            sourceSets,
            defaultSS
        )

        sourceSets.all {
            configureSourceSet(project, it, defaultSS.classSpec)
        }

        with(project) {
            var taskGraphLocked = false

            gradle.taskGraph.whenReady { taskGraphLocked = true }

            PluginBindings.values().forEach {
                pluginManager.withPlugin(it.pluginId) { _ ->
                    it.handler(project, extension) { name, onSpec ->
                        sourceSets.maybeCreate(name).apply {
                            onSpec(classSpec)

                            extraSpecs.all { extra ->
                                if (taskGraphLocked) {
                                    throw IllegalStateException("Can't call 'forClass' after taskGraph was built!")
                                }
                                onSpec(extra)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun configureSourceSet(
        project: Project,
        sourceSet: BuildConfigSourceSetInternal,
        defaultSpec: BuildConfigClassSpecInternal
    ) {
        logger.debug("Creating buildConfig sourceSet '${sourceSet.name}' for $project")

        val buildDir = sourceSet.name
        val prefix = buildDir.takeUnless { it.equals("main", ignoreCase = true) }?.capitalize() ?: ""

        createGenerateTask(
            project, prefix, buildDir, sourceSet.classSpec, defaultSpec,
            descriptionSuffix = "'${sourceSet.name}' source"
        )

        sourceSet.extraSpecs.all {
            val childPrefix = prefix + it.name.capitalize()

            createGenerateTask(
                project, childPrefix, buildDir, it, defaultSpec,
                descriptionSuffix = "'${it.name}' spec on '${sourceSet.name}' source"
            )
        }
    }

    private fun createGenerateTask(
        project: Project,
        prefix: String,
        buildDir: String,
        spec: BuildConfigClassSpecInternal,
        defaultSpec: BuildConfigClassSpecInternal,
        descriptionSuffix: String
    ) =
        project.tasks.create("generate${prefix}BuildConfig", BuildConfigTask::class.java).apply {
            group = "BuildConfig"
            description = "Generates the build constants class for $descriptionSuffix"

            fields = spec.fields.lazyValues
            outputDir = project.file("${project.buildDir}/generated/source/buildConfig/$buildDir")

            project.afterEvaluate {
                className = spec.className ?: defaultSpec.className ?: "${prefix}BuildConfig"
                packageName = spec.packageName ?: defaultSpec.packageName ?: project.defaultPackage
                    .replace("[^a-zA-Z._$]".toRegex(), "_")
                language = spec.language ?: defaultSpec.language ?: BuildConfigLanguage.JAVA
            }

            spec.generateTask = this
        }

    private val Project.defaultPackage
        get() = group
            .toString()
            .takeUnless { it.isEmpty() }
            ?.let { "$it.${project.name}" }
            ?: project.name

    companion object {

        const val DEFAULT_SOURCE_SET_NAME = "main"

    }

}
