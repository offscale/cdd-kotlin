package openapi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UriUtilsTest {

  @Test
  fun testIsAbsoluteUri() {
    assertTrue(isAbsoluteUri("http://example.com"))
    assertTrue(isAbsoluteUri("https://example.com"))
    assertTrue(isAbsoluteUri("file:///path/to/file"))
    assertFalse(isAbsoluteUri("/relative/path"))
    assertFalse(isAbsoluteUri("relative/path"))
  }

  @Test
  fun testResolveUri() {
    assertEquals(
        "http://example.com/path", resolveUri("http://example.com/base", "http://example.com/path"))

    assertEquals("http://example.com/root", resolveUri("http://example.com/base/sub", "/root"))
    assertEquals("http://example.com/root", resolveUri("http://example.com", "/root"))

    assertEquals(
        "http://example.com/base/relative", resolveUri("http://example.com/base/", "relative"))
    assertEquals("http://example.com/relative", resolveUri("http://example.com/base", "relative"))
  }
}
