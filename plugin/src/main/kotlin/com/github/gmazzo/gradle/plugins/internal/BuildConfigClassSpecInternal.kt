package com.github.gmazzo.gradle.plugins.internal

import com.github.gmazzo.gradle.plugins.BuildConfigClassSpec
import com.github.gmazzo.gradle.plugins.BuildConfigField
import com.github.gmazzo.gradle.plugins.BuildConfigGenerator
import com.github.gmazzo.gradle.plugins.tasks.BuildConfigTask

internal interface BuildConfigClassSpecInternal : BuildConfigClassSpec {

    var className: String?

    var packageName: String?

    var language: BuildConfigGenerator?

    val fields: Map<String, BuildConfigField>

    override var generateTask: BuildConfigTask

}
