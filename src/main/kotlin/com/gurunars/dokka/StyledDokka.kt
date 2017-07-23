package com.gurunars.dokka

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.jetbrains.dokka.DokkaConfiguration
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

                val nameToModuleMap = mutableMapOf<String, Project>()
                modules.forEach { nameToModuleMap.put("${it.group}:${it.name}:${it.version}", it) }

                modules.forEach {
                    val task = it.tasks.getByName("dokka") as DokkaAndroidTask
                    val moduleDepFullNames = mutableSetOf<String>().apply {
                        it.configurations.map {
                            it.allDependencies.map {
                                val name = "${it.group}:${it.name}:${it.version}"
                                if (nameToModuleMap.contains(name)) {
                                    add(name)
                                }
                            }
                        }
                    }

                    task.apply {
                        setMustRunAfter(moduleDepFullNames.map {
                            nameToModuleMap[it]!!.getTasksByName("dokka", true)
                        }.flatten())
                        moduleName=it.name
                        outputFormat = "html"
                        outputDirectory = "${project.projectDir.absolutePath}/html-docs"
                        externalDocumentationLinks.addAll(
                            moduleDepFullNames.map { nameToModuleMap[it]!!.name }.map {
                                DokkaConfiguration.ExternalDocumentationLink.Builder(
                                    "file://${project.projectDir.absolutePath}/html-docs/${it}/"
                                ).build()
                            }
                        )
                    }
                }

                setMustRunAfter(
                    modules.map { it.getTasksByName("dokka", true) }.flatten()
                )

                doLast {
                    beautify(project, modules)
                }
            }
        }
    }

}
