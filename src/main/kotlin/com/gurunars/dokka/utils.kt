package com.gurunars.dokka

import org.gradle.api.Project
import org.jsoup.Jsoup
import org.jsoup.nodes.*
import org.jsoup.parser.Tag
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset


private val dimRegexp = Regex("""^\d+x\d+$""")


private class ParamParseStateMachine(
        private val consumer: (tags: Element) -> Unit
) {
    private enum class ParamParseState {
        NOT_STARTED,
        STARTED,
        LINK,
        CODE
    }

    private var state = ParamParseState.NOT_STARTED
    private var resultAccumulator: Element = initAccumulator()

    private fun initAccumulator() = Element(Tag.valueOf("div"), "", Attributes().apply {
        put("class", "parameter")
    })

    private fun transit(child: Node): ParamParseState {

        fun node(name: String, state: ParamParseState): ParamParseState {
            if (child is Element && child.tagName() == name) {
                resultAccumulator.appendChild(child.clone())
                child.attr("class", "hidden")
                return state
            } else {
                return this.state
            }
        }

        return when (state) {
            ParamParseState.NOT_STARTED ->
                if (child is Element &&
                        child.tagName() == "h3" &&
                        child.text() == "Parameters") ParamParseState.STARTED else state
            ParamParseState.STARTED -> node("a", ParamParseState.LINK)
            ParamParseState.LINK -> node("code", ParamParseState.CODE)
            ParamParseState.CODE -> {
                if (child is TextNode && child.text().trim().startsWith("- ")) {
                    resultAccumulator.appendText(child.text().trim())
                    child.text("")
                }
                consumer(resultAccumulator)
                resultAccumulator = initAccumulator()
                ParamParseState.STARTED
            }
        }
    }

    fun process(child: Node) {
        state = transit(child)
    }

}


private fun beautifyParameters(doc: Document) {
    val parameters = Element(Tag.valueOf("div"), "", Attributes().apply {
        put("class", "parameters")
    })
    val rmNodes = mutableListOf<Node>()
    val stateMachine = ParamParseStateMachine({
        parameters.appendChild(it)
    })
    rmNodes.forEach { it.remove() }
    doc.body().childNodes().forEach {
        stateMachine.process(it)
    }
    doc.body().appendChild(parameters)
}


private fun orderAllTypes(doc: Document) {
    doc.body().select("table").first()?.apply {
        insertChildren(0,
                select("tr").sortedWith(
                        compareBy<Element> {
                            it.select("a").first().text()
                        }
                )
        )
    }
}


private fun formatBreadCrumbs(moduleName: String, doc: Document) {
    val tag = Element(Tag.valueOf("div"), "", Attributes().apply {
        put("class", "breadcrumbs")
    })

    tag.appendText(" / ")
    tag.appendChild(Element(Tag.valueOf("a"), "", Attributes().apply {
        put("href", "/")
    }).apply {
        appendText("index")
    })

    if (doc.body().select(":root > br").isEmpty()) { // All types
        tag.appendText(" / ")
        tag.appendChild(Element(Tag.valueOf("a"), "", Attributes().apply {
            put("href", "/" + moduleName)
        }).apply {
            appendText(moduleName)
        })
        tag.appendText(" / ")
        tag.appendChild(Element(Tag.valueOf("a"), "", Attributes().apply {
            put("href", "/$moduleName/alltypes/")
        }).apply {
            appendText("All Types")
        })
        orderAllTypes(doc)
    } else {
        for (it in doc.body().select(":root > *")) {
            if (it.tagName() == "br") {
                break
            }
            tag.appendText(" / ")
            tag.appendChild(it.clone())
            it.remove()
        }

        doc.select("br").remove()
    }

    doc.body().appendChild(tag)
}


private fun replaceImageLinksWithImgs(doc: Document) {
    doc.select("a[href]").filter { dimRegexp.matches(it.text()) }.forEach {
        val parts = it.text().split("x")
        it.replaceWith(Element(Tag.valueOf("img"), "", Attributes().apply {
            put("src", it.attr("href"))
            put("width", parts[0])
            put("height", parts[1])
        }))
    }
}


private fun beautifyHtml(rootPath: String, moduleName: String, file: File) {
    val doc = Jsoup.parse(
            file.readText()
                .replace("&nbsp;/&nbsp;", "")
                .replace("file:$rootPath/html-docs/", "/"),
            "UTF-8"
    )

    replaceImageLinksWithImgs(doc)
    formatBreadCrumbs(moduleName, doc)
    beautifyParameters(doc)

    file.writeText(doc.outerHtml())
}


private fun copy(from: String, to: String) {
    val source = File(from)
    val target = File(to)
    if (!source.exists()) {
        return
    }
    target.deleteRecursively()
    source.copyRecursively(target, true)
}


private fun InputStream.readTextAndClose(charset: Charset = Charsets.UTF_8): String {
    return this.bufferedReader(charset).use { it.readText() }
}


internal fun beautify(project: Project, modules: Collection<Project>) {
    val root = project.projectDir.absolutePath

    with(File("$root/html-docs/style.css")) {
        delete()
        createNewFile()
        writeText(
                ParamParseStateMachine::class.java.getResourceAsStream("/style.css").readTextAndClose()
        )
    }

    modules.forEach {
        val module = it

        copy("${module.name}/static", "$root/html-docs/${module.name}/static")

        File("$root/html-docs/${module.name}").walk().forEach {
            if (it.isFile && it.name.endsWith(".html")) {
                beautifyHtml(root, module.name, it)
            }
        }

    }

    val projectDivs = modules.map {
        val installLine = """
            implementation ('${it.group}:${it.name}:${project.version}@aar') {
                transitive = true
            }
        """.trimIndent()

        """
        <div class="section">
            <h3><a href="${it.name}">${it.name} (${project.version})</a></h3>
            ${
                if (it.description != null)
                    "<p>${it.description}</p>"
                else
                    ""
            }
            <pre class="install-line">$installLine</pre>
        </div>
        """
    }

    val extras = project.extensions.extraProperties

    val mavenRepo =
        if (extras.has("mavenRepoUrl"))
            extras.get("mavenRepoUrl")
        else
            "<TBD>"

    val installLine = """
    allprojects {
        repositories {
            ...
            maven { url $mavenRepo }
            ...
        }
    }
    """.trimIndent()

    val indexFile = File("$root/html-docs/index.html")
    indexFile.writeText("""
    <html>
        <head>
            <meta charset="UTF-8">
            <title>${project.name} (${project.version})</title>
            <link rel="stylesheet" href="style.css">
        </head>
        <body>
            <div class="breadcrumbs">
                / <a href="/">index</a>
            </div>

            <div class="section">
                <p>
                ${project.description}
                </p>

                <p>
                In order to fetch the modules as dependencies add the following to your
                top level <b>build.gradle</b>:
                </p>

                <pre class="install-line">${installLine}</pre>
            </div>

            ${projectDivs.joinToString("\n")}
        </body>
    </html>
    """)

}

