package dev.vfyjxf.phonin.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction

/**
 * Fails the build if any scanned Java source uses an inline fully-qualified class name
 * (e.g. `dev.vfyjxf.phonin.model.Reading`) instead of importing it. This is the explicit style rule:
 * always `import` types, never spell out their package inline.
 *
 * The detection regex matches a dotted path of two or more lowercase-led segments ending in
 * a Capitalized name — so real FQNs like `java.util.Map` or `dev.vfyjxf.phonin.model.Reading` are
 * flagged, while method calls (`map.put`), constants (`System.out`), and generic calls
 * (`Collections.<X>emptyList()`) are not. Comment and string-literal content is stripped
 * first; package/import lines and comment continuations are skipped.
 */
abstract class NoInlineFqnTask : DefaultTask() {

    @get:InputFiles
    abstract val sources: ConfigurableFileCollection

    init {
        group = "verification"
        description = "Fail if any Java source uses an inline fully-qualified class name instead of importing it."
    }

    @TaskAction
    fun check() {
        val fqn = Regex("""\b([a-z][A-Za-z0-9_]*\.){2,}[A-Z][A-Za-z0-9_]*\b""")
        val stringLit = Regex(""""([^"\\]|\\.)*"""")
        val blockComment = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val offenders = mutableListOf<String>()

        sources.files.filter { it.isFile }.forEach { file ->
            file.readLines().forEachIndexed { idx, raw ->
                var code = raw.substringBefore("//")
                code = blockComment.replace(code, "")
                code = stringLit.replace(code, "\"\"")
                val trimmed = code.trim()
                if (trimmed.isEmpty()) return@forEachIndexed
                if (trimmed.startsWith("package ") || trimmed.startsWith("import ")) return@forEachIndexed
                if (trimmed.startsWith("*") || trimmed.startsWith("/*")) return@forEachIndexed
                for (m in fqn.findAll(code)) {
                    val rel = file.relativeTo(project.rootProject.projectDir)
                    offenders.add("$rel:${idx + 1}: import `${m.value}` instead of using the fully-qualified name")
                }
            }
        }

        check(offenders.isEmpty()) {
            "Inline fully-qualified class names are banned — import the type instead. Found:\n" +
                offenders.joinToString("\n")
        }
    }
}
