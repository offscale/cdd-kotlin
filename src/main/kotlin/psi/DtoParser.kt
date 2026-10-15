/**
 * Parses Kotlin source code via PSI to extract OpenAPI [SchemaDefinition]s and [SchemaProperty]s.
 */
package psi

import domain.SchemaDefinition
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import psi.dto.DataClassPsiParser

/**
 * Service that parses Kotlin source code to extract OpenAPI [SchemaDefinition]s. Analyzes Data
 * Classes (Objects), Enums, Sealed Interfaces, and Typealiases by delegating to
 * [DataClassPsiParser] and [psi.dto.SchemaDocTagParser].
 */
class DtoParser {
  /**
   * Parses the provided Kotlin source code into a list of [SchemaDefinition]s.
   *
   * @param sourceCode The content of the Kotlin file.
   * @return A list of extracted Schema Definitions.
   */
  fun parse(sourceCode: String): List<SchemaDefinition> {
    val psiFactory = PsiInfrastructure.createPsiFactory()
    val file = psiFactory.createFile("Analysis.kt", sourceCode)

    val classes = file.collectDescendantsOfType<KtClass>()
    val aliases = file.collectDescendantsOfType<KtTypeAlias>()

    val sealedInterfaces = classes.filter { it.isInterface() && it.isSealed() }
    val sealedNames = sealedInterfaces.mapNotNull { it.name }.toSet()
    val sealedSubtypeIndex = DataClassPsiParser.buildSealedSubtypeIndex(classes, sealedNames)

    val classSchemas =
        classes.mapNotNull { ktClass ->
          when {
            ktClass.isEnum() -> DataClassPsiParser.parseEnum(ktClass)
            ktClass.isData() -> DataClassPsiParser.parseDataClass(ktClass)
            ktClass.isInterface() && ktClass.isSealed() -> {
              val subtypes = sealedSubtypeIndex[ktClass.name].orEmpty()
              DataClassPsiParser.parseSealedInterface(ktClass, subtypes)
            }
            else -> null
          }
        }

    val aliasSchemas = aliases.mapNotNull { DataClassPsiParser.parseTypeAlias(it) }

    return classSchemas + aliasSchemas
  }
}
