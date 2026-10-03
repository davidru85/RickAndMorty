package io.github.davidru85.multiverse.core.presentation

import java.io.File
import java.lang.reflect.Modifier
import java.lang.reflect.Type
import java.net.JarURLConnection
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `TEST-UNIT-012`, its `:core:presentation` half (`REQ-NFR-001`, `AC-REQ-NFR-001-2`): the module's
 * compiled public surface names no Android, Compose, Apple, HTTP or serialization type, no type of
 * the implementation module and no DTO. The surface is read from the classes the Android target
 * compiles — every public class of the module's package, with its public members' generic
 * signatures, supertypes and fields — so the assertion is about what a consumer can see, not about
 * source text. The module has no platform source set; Apple-side types could only arrive through one.
 */
class PresentationPublicSurfaceTest {
    private val root = "io.github.davidru85.multiverse.core.presentation"

    private val forbidden =
        listOf(
            "android.",
            "androidx.",
            "platform.",
            "io.ktor.",
            "kotlinx.serialization.",
            "okhttp3.",
            "io.github.davidru85.multiverse.core.data.",
        )

    @Test
    fun `TEST-UNIT-012 given_the_compiled_module_when_its_public_surface_is_read_then_no_platform_ui_http_or_dto_type_appears`() {
        val classes = publicClasses()
        val referenced = classes.flatMap(::signatureTypes).toSet()

        assertTrue(
            classes
                .map {
                    it.simpleName
                }.containsAll(listOf("CopyKey", "CopyKeys", "LoadState", "CharacterCardUi", "PresentationFormatters", "DisplayText")),
            "TEST-UNIT-012: the surface was read: ${classes.map { it.name }}",
        )
        assertEquals(
            emptyList(),
            referenced.filter { name -> forbidden.any(name::startsWith) || name.endsWith("Dto") }.sorted(),
            "TEST-UNIT-012: the public surface stays platform-free (AC-REQ-NFR-001-2)",
        )
    }

    /**
     * The module's own classes: only the location its main classes were loaded from is read, so the
     * test source set — this class and the parity verifier — is never mistaken for module surface.
     */
    private fun publicClasses(): List<Class<*>> {
        val loader = javaClass.classLoader
        val main =
            LoadState::class.java.protectionDomain.codeSource.location
                .toURI()
        return loader
            .getResources(root.replace('.', '/'))
            .toList()
            .filter { it.toString().contains(main.toString().removePrefix("file:").trimEnd('/')) }
            .flatMap(::classNames)
            .distinct()
            .map { Class.forName(it, false, loader) }
            .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }
    }

    private fun classNames(url: URL): List<String> =
        when (url.protocol) {
            "file" ->
                File(url.toURI())
                    .walkTopDown()
                    .filter { it.isFile && it.name.endsWith(".class") }
                    .map { file ->
                        root + "." +
                            file
                                .relativeTo(File(url.toURI()))
                                .invariantSeparatorsPath
                                .removeSuffix(".class")
                                .replace('/', '.')
                    }.toList()
            "jar" -> {
                val connection = url.openConnection() as JarURLConnection
                connection.jarFile
                    .entries()
                    .toList()
                    .map { it.name }
                    .filter { it.startsWith(root.replace('.', '/')) && it.endsWith(".class") }
                    .map { it.removeSuffix(".class").replace('/', '.') }
            }
            else -> emptyList()
        }

    private fun signatureTypes(type: Class<*>): List<String> {
        val types = mutableListOf<Type>()
        type.genericSuperclass?.let(types::add)
        types += type.genericInterfaces
        type.methods.filter { Modifier.isPublic(it.modifiers) }.forEach { method ->
            types += method.genericReturnType
            types += method.genericParameterTypes
        }
        type.constructors.filter { Modifier.isPublic(it.modifiers) }.forEach { types += it.genericParameterTypes }
        type.fields.filter { Modifier.isPublic(it.modifiers) }.forEach { types += it.genericType }
        return types.flatMap { qualifiedNames.findAll(it.typeName).map { match -> match.value }.toList() }
    }

    private val qualifiedNames = Regex("[A-Za-z_][\\w$]*(\\.[A-Za-z_][\\w$]*)+")
}
