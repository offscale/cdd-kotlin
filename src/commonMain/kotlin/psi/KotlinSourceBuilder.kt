/**
 * Structured Kotlin source builder DSL, identifier escaping (`escapeKotlinKeyword`), and shared
 * model schema filtering utilities for PSI and code generators.
 */
package psi

import domain.SchemaDefinition

private val KOTLIN_KEYWORDS =
    setOf(
        "as",
        "break",
        "class",
        "continue",
        "do",
        "else",
        "false",
        "for",
        "fun",
        "if",
        "in",
        "interface",
        "is",
        "null",
        "object",
        "package",
        "return",
        "super",
        "this",
        "throw",
        "true",
        "try",
        "typealias",
        "typeof",
        "val",
        "var",
        "when",
        "while")

/**
 * Escapes [name] with backticks if it matches a reserved Kotlin hard keyword.
 *
 * @param name The raw identifier name.
 * @return The escaped identifier string (``name`` if reserved, otherwise [name]).
 */
fun escapeKotlinKeyword(name: String): String {
  return if (name in KOTLIN_KEYWORDS) "`$name`" else name
}

/**
 * Capitalizes the first character of [name] (e.g. `"user"` -> `"User"`).
 *
 * @param name The identifier string.
 * @return The string with its first character uppercased.
 */
fun capitalizeName(name: String): String =
    if (name.isEmpty()) name else name[0].uppercaseChar() + name.substring(1)

/**
 * Decapitalizes the first character of [name] (e.g. `"User"` -> `"user"`).
 *
 * @param name The identifier string.
 * @return The string with its first character lowercased.
 */
fun decapitalizeName(name: String): String =
    if (name.isEmpty()) name else name[0].lowercaseChar() + name.substring(1)

/**
 * Filters a list of [SchemaDefinition]s down to concrete object entity schemas eligible for DAO,
 * database table, route, and seeder generation.
 *
 * @param schemas The full list of [SchemaDefinition]s.
 * @return Filtered list of concrete entity object schemas.
 */
fun filterModelSchemas(schemas: List<SchemaDefinition>): List<SchemaDefinition> {
  return schemas.filter {
    it.type == "object" &&
        it.enumValues == null &&
        it.anyOf.isEmpty() &&
        it.oneOf.isEmpty() &&
        it.anyOfSchemas.isEmpty() &&
        it.oneOfSchemas.isEmpty() &&
        !it.safeName.contains("ExternalAccount") &&
        !it.safeName.contains("PaymentSource")
  }
}

/**
 * Structured builder for generating formatted Kotlin source files with package declarations,
 * imports, KDoc blocks, and indented declarations.
 *
 * @property packageName Optional package name declared at the top of the file.
 */
class KotlinSourceBuilder(
    /** Optional package name declared at the top of the file. */
    val packageName: String = ""
) {
  private val imports = mutableListOf<String>()
  private val body = StringBuilder()
  private var indentLevel = 0

  /**
   * Adds one or more import paths to the source file.
   *
   * @param importPaths Fully qualified import strings (without the `import ` keyword).
   */
  fun import(vararg importPaths: String) {
    importPaths.forEach { imports.add(it) }
  }

  /**
   * Appends a multi-line KDoc block at the current indentation level.
   *
   * @param lines Lines of documentation text (including `@param`, `@property`, or `@return` tags).
   */
  fun kdoc(vararg lines: String) {
    line("/**")
    lines.forEach { docLine ->
      if (docLine.isEmpty()) {
        line(" *")
      } else {
        line(" * $docLine")
      }
    }
    line(" */")
  }

  /**
   * Appends a single line of code at the current indentation level, or an empty line if [text] is
   * empty.
   *
   * @param text The line content to append.
   */
  fun line(text: String = "") {
    if (text.isEmpty()) {
      body.appendLine()
    } else {
      body.append("    ".repeat(indentLevel)).appendLine(text)
    }
  }

  /**
   * Appends a raw pre-formatted string fragment directly to the body.
   *
   * @param rawText The raw source text to append.
   */
  fun raw(rawText: String) {
    body.append(rawText)
  }

  /**
   * Opens a brace-delimited block (`header { ... }`), increments indentation for [blockContent],
   * and closes with `}`.
   *
   * @param header The block header before `{` (e.g., `"class MyClass"` or `"fun test()"`).
   * @param suffix Optional trailing text after the closing `}` (e.g., `""`).
   * @param blockContent Builder lambda for the block interior.
   */
  fun block(header: String, suffix: String = "", blockContent: KotlinSourceBuilder.() -> Unit) {
    line("$header {")
    indentLevel++
    this.blockContent()
    indentLevel--
    line("}$suffix")
  }

  /**
   * Renders the complete Kotlin source file string.
   *
   * @return The formatted Kotlin source code.
   */
  fun build(): String {
    val out = StringBuilder()
    if (packageName.isNotEmpty()) {
      out.appendLine("package $packageName")
      out.appendLine()
    }
    if (imports.isNotEmpty()) {
      imports.forEach { out.appendLine("import $it") }
      out.appendLine()
    }
    out.append(body)
    return out.toString()
  }
}

/**
 * Builds a Kotlin source file using the [KotlinSourceBuilder] DSL.
 *
 * @param packageName Package name for the generated file.
 * @param init DSL initialization block.
 * @return The generated Kotlin source code string.
 */
fun buildKotlinFile(packageName: String = "", init: KotlinSourceBuilder.() -> Unit): String {
  val builder = KotlinSourceBuilder(packageName)
  builder.init()
  return builder.build()
}
