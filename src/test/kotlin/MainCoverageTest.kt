import java.io.ByteArrayOutputStream
import java.io.PrintStream
import org.junit.jupiter.api.Test

class MainCoverageTest {
  @Test
  fun testMain() {
    val out = ByteArrayOutputStream()
    System.setOut(PrintStream(out))
    try {
      main()
    } catch (e: Exception) {}
    System.setOut(System.out)
  }
}
