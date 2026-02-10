package psi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReferenceResolverTest {

    @Test
    fun `resolveRefToType handles Internal Pointers`() {
        assertEquals("User", ReferenceResolver.resolveRefToType("#/components/schemas/User"))
        assertEquals("Pet", ReferenceResolver.resolveRefToType("#/definitions/Pet"))
        assertEquals("Config", ReferenceResolver.resolveRefToType("#/components/parameters/Config"))
    }

    @Test
    fun `resolveRefToType handles External Files`() {
        assertEquals("Address", ReferenceResolver.resolveRefToType("./models/Address.json"))
        assertEquals("Order", ReferenceResolver.resolveRefToType("../contracts/Order.yaml"))
        assertEquals("Profile", ReferenceResolver.resolveRefToType("http://example.com/schemas/Profile.yml"))
    }

    @Test
    fun `resolveRefToType handles External Files with Fragments`() {
        assertEquals("Detail", ReferenceResolver.resolveRefToType("Order.yaml#/definitions/Detail"))
        assertEquals("Metadata", ReferenceResolver.resolveRefToType("http://api.com/spec.json#/components/schemas/Metadata"))
    }

    @Test
    fun `resolveRefToType handles Simple Names (Backwards Compat)`() {
        assertEquals("User", ReferenceResolver.resolveRefToType("User"))
    }

    @Test
    fun `resolveRefToType handles lists wrapped in definitions`() {
        // Edge case where key might be complex
        assertEquals("My_Type", ReferenceResolver.resolveRefToType("#/definitions/My_Type"))
    }

    @Test
    fun `resolveRefToType handles fragment-only refs`() {
        assertEquals("User", ReferenceResolver.resolveRefToType("#User"))
    }

    @Test
    fun `resolveRefToType handles filenames without extensions`() {
        assertEquals("Address", ReferenceResolver.resolveRefToType("models/Address"))
    }

    @Test
    fun `resolveRefToType falls back on empty path`() {
        assertEquals("#", ReferenceResolver.resolveRefToType("#"))
    }
}
