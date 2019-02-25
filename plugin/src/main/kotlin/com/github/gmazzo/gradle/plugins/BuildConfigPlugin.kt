package com.github.gmazzo.gradle.plugins

import com.github.gmazzo.gradle.plugins.internal.DefaultBuildConfigExtension
import com.github.gmazzo.gradle.plugins.internal.DefaultBuildConfigSourceSet
import com.github.gmazzo.gradle.plugins.tasks.BuildConfigTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.plugins.DslObject
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.internal.reflect.Instantiator
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import javax.inject.Inject

class BuildConfigPlugin @Inject constructor(
    private val instantiator: Instantiator
) : Plugin<Project> {

    private val logger = Logging.getLogger(javaClass)

    override fun apply(project: Project) {
        val sourceSets = project.container(BuildConfigSourceSet::class.java, ::DefaultBuildConfigSourceSet)

        val extension = project.extensions.create(
            BuildConfigExtension::class.java,
            "buildConfig",
            DefaultBuildConfigExtension::class.java,
            sourceSets.create("main")
        ) as DefaultBuildConfigExtension

        var kotlinDetected = false

        project.plugins.withId("org.jetbrains.kotlin.jvm") {
            logger.debug("Configuring buildConfig '${BuildConfigLanguage.KOTLIN}' language for $project")

            kotlinDetected = true
            extension.language(BuildConfigLanguage.KOTLIN)
        }

        project.plugins.withType(JavaPlugin::class.java) {
            project.convention.getPlugin(JavaPluginConvention::class.java).sourceSets.all { ss ->
                logger.debug("Creating buildConfig sourceSet '${ss.name}' for $project")

                val prefix = ss.name.takeUnless { it == "main" }?.capitalize() ?: ""
                val sourceSet = sourceSets.maybeCreate(ss.name) as DefaultBuildConfigSourceSet
                DslObject(ss).convention.plugins[ss.name] = sourceSet

                project.tasks.create("generate${prefix}BuildConfig", BuildConfigTask::class.java).apply {
                    fields = sourceSet.fields.lazyValues
                    outputDir = project.file("${project.buildDir}/generated/source/buildConfig/${ss.name}")

                    ss.java.srcDir(outputDir)
                    project.tasks.getAt(ss.compileJavaTaskName).dependsOn(this)

                    if (kotlinDetected) {
                        DslObject(ss).convention.getPlugin(KotlinSourceSet::class.java).apply {
                            kotlin.srcDir(outputDir)
                        }
                        project.tasks.getAt("compileKotlin").dependsOn(this)
                    }

                    project.afterEvaluate {
                        className = extension.className ?: "${prefix}BuildConfig"
                        packageName = extension.packageName ?: project.group.toString()
                        language = extension.language ?: BuildConfigLanguage.JAVA
                    }
                }
            }
        }
    }

}
