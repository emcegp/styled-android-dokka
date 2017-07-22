package com.gurunars.dokka

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.jetbrains.dokka.gradle.DokkaAndroidTask


private fun requirePlugins(vararg pluginNames: String) : (project: Project) -> Boolean {
    return { project ->
        pluginNames.all { project.plugins.findPlugin(it) != null }
    }
}


class StyledDokka : Plugin<Project> {

    override fun apply(project: Project) {
        project.gradle.projectsEvaluated {
            project.task("dokka").apply {
                description = "Aggregate API docs of all subprojects with custom styles."
                group = JavaBasePlugin.DOCUMENTATION_GROUP

                val modules = project.subprojects.filter(requirePlugins(
                    "com.android.library", "org.jetbrains.dokka-android", "kotlin-android"
                ))

                modules.forEach {
                    val task = it.tasks.getByName("dokka") as DokkaAndroidTask
                    task.apply {
                        outputFormat = "html"
                        outputDirectory = "${project.projectDir.absolutePath}/html-docs"
                    }
                }

                setMustRunAfter(
                    modules.map { it.getTasksByName("dokka", true) }.flatten()
                )

                doLast { beautify(project, modules) }
            }
        }
    }

}
