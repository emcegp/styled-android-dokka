package com.gurunars.dokka

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.jetbrains.dokka.ExternalDocumentationLinkImpl
import org.jetbrains.dokka.gradle.DokkaTask
import java.net.URL


class StyledDokka : Plugin<Project> {

    override fun apply(project: Project) {
        project.gradle.projectsEvaluated {
            project.task("dokka").apply {
                description = "Aggregate API docs of all subprojects with custom styles."
                group = JavaBasePlugin.DOCUMENTATION_GROUP

                val modules = project.subprojects.filter {
                    it.plugins.findPlugin("com.android.library") != null
                }

                modules.forEach {
                    it.tasks.replace("dokka", DokkaTask::class.java).apply {
                        moduleName=it.name
                        sourceDirs = it.files("src/main/kotlin")
                        outputFormat = "html"
                        outputDirectory = "html-docs"
                        includes = listOf("README.md")
                        externalDocumentationLinks = mutableListOf(
                                ExternalDocumentationLinkImpl(
                                        url= URL("https://developer.android.com/reference/"),
                                        packageListUrl= URL("https://developer.android.com/reference/package-list")
                                )
                        )
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
