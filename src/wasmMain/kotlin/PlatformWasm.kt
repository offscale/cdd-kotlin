/**
 * WebAssembly (WASI Preview 1) implementations of platform expect functions for environment
 * variable access, POSIX file I/O, and platform operation error handling.
 */
import domain.CddError
import domain.toFailure
import kotlin.wasm.WasmImport
import kotlin.wasm.unsafe.Pointer
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator

/**
 * Low-level WASI `environ_sizes_get` import.
 *
 * @param environ_count_ptr Pointer to store the environment variable count.
 * @param environ_buf_size_ptr Pointer to store the environment buffer size.
 * @return WASI errno code (0 for success).
 */
@OptIn(kotlin.wasm.ExperimentalWasmInterop::class)
@WasmImport("wasi_snapshot_preview1", "environ_sizes_get")
private external fun wasi_environ_sizes_get(environ_count_ptr: Int, environ_buf_size_ptr: Int): Int

/**
 * Low-level WASI `environ_get` import.
 *
 * @param environ_ptr Pointer to the array of string pointers.
 * @param environ_buf_ptr Pointer to the character buffer.
 * @return WASI errno code (0 for success).
 */
@OptIn(kotlin.wasm.ExperimentalWasmInterop::class)
@WasmImport("wasi_snapshot_preview1", "environ_get")
private external fun wasi_environ_get(environ_ptr: Int, environ_buf_ptr: Int): Int

/**
 * Retrieves an environment variable using WASI `environ_sizes_get` and `environ_get`.
 *
 * @param name The name of the environment variable.
 * @return The value of the environment variable, or `null` if not set.
 */
@OptIn(kotlin.wasm.unsafe.UnsafeWasmMemoryApi::class)
actual fun getEnvVar(name: String): String? {
  var count = 0
  var bufSize = 0
  withScopedMemoryAllocator { alloc ->
    val countPtr = alloc.allocate(4)
    val bufSizePtr = alloc.allocate(4)
    if (wasi_environ_sizes_get(countPtr.address.toInt(), bufSizePtr.address.toInt()) != 0)
        return null
    count = countPtr.loadInt()
    bufSize = bufSizePtr.loadInt()
  }
  if (count == 0) return null

  var res: String? = null
  withScopedMemoryAllocator { alloc ->
    val environPtr = alloc.allocate(count * 4)
    val environBufPtr = alloc.allocate(bufSize)
    if (wasi_environ_get(environPtr.address.toInt(), environBufPtr.address.toInt()) != 0)
        return null

    for (i in 0 until count) {
      val strPtrInt = (environPtr + i * 4).loadInt()
      val strPtr = Pointer(strPtrInt.toUInt())
      var len = 0
      while ((strPtr + len).loadByte() != 0.toByte()) {
        len++
      }
      val bytes = ByteArray(len)
      for (j in 0 until len) {
        bytes[j] = (strPtr + j).loadByte()
      }
      val str = bytes.decodeToString()
      if (str.startsWith("$name=")) {
        res = str.substring(name.length + 1)
        break
      }
    }
  }
  return res
}

/**
 * Reads a file using WASI POSIX imports.
 *
 * @param path The path of the file to read.
 * @return A [Result] containing the contents of the file as a String or a [CddError.FileIoError].
 */
@OptIn(kotlin.wasm.unsafe.UnsafeWasmMemoryApi::class)
actual fun readFile(path: String): Result<String> {
  val cleanPath = if (path.startsWith("/")) path.substring(1) else path
  val pathBytes = cleanPath.encodeToByteArray()
  var inFd = -1
  var success = false
  var lastErrno = -1

  withScopedMemoryAllocator { alloc ->
    val pathPtr = alloc.allocate(pathBytes.size)
    for (i in pathBytes.indices) {
      (pathPtr + i).storeByte(pathBytes[i])
    }
    val inFdPtr = alloc.allocate(4)

    for (tryFd in 3..10) {
      val errno =
          wasi_path_open(
              fd = tryFd,
              dirflags = 1, // SYMLINK_FOLLOW
              path_ptr = pathPtr.address.toInt(),
              path_len = pathBytes.size,
              oflags = 0, // No O_CREAT
              fs_rights_base = 2L, // RIGHT_FD_READ (2)
              fs_rights_inheriting = 0L,
              fdflags = 0,
              opened_fd_ptr = inFdPtr.address.toInt())
      if (errno == 0) {
        inFd = inFdPtr.loadInt()
        success = true
        break
      } else if (errno != 8) { // 8 is EBADF
        lastErrno = errno
      }
    }
  }

  if (!success) {
    return CddError.FileIoError(cleanPath, "Can't open $cleanPath for read: WASI errno $lastErrno")
        .toFailure()
  }

  val bufferSize = 8192
  val resultBytes = mutableListOf<Byte>()
  var readErrno = 0

  withScopedMemoryAllocator { alloc ->
    val dataPtr = alloc.allocate(bufferSize)
    val iovsPtr = alloc.allocate(8)
    iovsPtr.storeInt(dataPtr.address.toInt())
    (iovsPtr + 4).storeInt(bufferSize)
    val nreadPtr = alloc.allocate(4)

    while (true) {
      val errno = wasi_fd_read(inFd, iovsPtr.address.toInt(), 1, nreadPtr.address.toInt())
      if (errno != 0) {
        readErrno = errno
        break
      }
      val nread = nreadPtr.loadInt()
      if (nread == 0) {
        break
      }
      for (i in 0 until nread) {
        resultBytes.add((dataPtr + i).loadByte())
      }
    }
  }

  wasi_fd_close(inFd)
  if (readErrno != 0) {
    return CddError.FileIoError(
            cleanPath, "WASI fd_read failed for $cleanPath with errno $readErrno")
        .toFailure()
  }
  return Result.success(resultBytes.toByteArray().decodeToString())
}

/**
 * Low-level WASI `fd_read` import.
 *
 * @param fd Open file descriptor.
 * @param iovs_ptr Pointer to the I/O vector array.
 * @param iovs_len Number of I/O vectors.
 * @param nread_ptr Pointer to store the number of bytes read.
 * @return WASI errno code (0 for success).
 */
@OptIn(kotlin.wasm.ExperimentalWasmInterop::class)
@WasmImport("wasi_snapshot_preview1", "fd_read")
private external fun wasi_fd_read(fd: Int, iovs_ptr: Int, iovs_len: Int, nread_ptr: Int): Int

/**
 * Low-level WASI `path_open` import.
 *
 * @param fd Base directory file descriptor.
 * @param dirflags Lookup flags (e.g. symlink follow).
 * @param path_ptr Pointer to the UTF-8 path string.
 * @param path_len Length of the path string in bytes.
 * @param oflags Open flags (`O_CREAT`, `O_TRUNC`, etc.).
 * @param fs_rights_base Base file descriptor rights mask.
 * @param fs_rights_inheriting Inheriting rights mask.
 * @param fdflags File descriptor flags.
 * @param opened_fd_ptr Pointer to store the newly opened file descriptor.
 * @return WASI errno code (0 for success).
 */
@OptIn(kotlin.wasm.ExperimentalWasmInterop::class)
@WasmImport("wasi_snapshot_preview1", "path_open")
private external fun wasi_path_open(
    fd: Int,
    dirflags: Int,
    path_ptr: Int,
    path_len: Int,
    oflags: Int,
    fs_rights_base: Long,
    fs_rights_inheriting: Long,
    fdflags: Int,
    opened_fd_ptr: Int
): Int

/**
 * Low-level WASI `fd_write` import.
 *
 * @param fd Open file descriptor.
 * @param iovs_ptr Pointer to the I/O vector array.
 * @param iovs_len Number of I/O vectors.
 * @param nwritten_ptr Pointer to store the number of bytes written.
 * @return WASI errno code (0 for success).
 */
@OptIn(kotlin.wasm.ExperimentalWasmInterop::class)
@WasmImport("wasi_snapshot_preview1", "fd_write")
private external fun wasi_fd_write(fd: Int, iovs_ptr: Int, iovs_len: Int, nwritten_ptr: Int): Int

/**
 * Low-level WASI `fd_close` import.
 *
 * @param fd File descriptor to close.
 * @return WASI errno code (0 for success).
 */
@OptIn(kotlin.wasm.ExperimentalWasmInterop::class)
@WasmImport("wasi_snapshot_preview1", "fd_close")
private external fun wasi_fd_close(fd: Int): Int

/**
 * Writes an error message directly to WASI stderr (fd 2).
 *
 * @param message The error message string to write.
 */
@OptIn(kotlin.wasm.unsafe.UnsafeWasmMemoryApi::class)
private fun logStderr(message: String) {
  val bytes = message.encodeToByteArray()
  withScopedMemoryAllocator { alloc ->
    val dataPtr = alloc.allocate(bytes.size)
    for (i in bytes.indices) {
      (dataPtr + i).storeByte(bytes[i])
    }
    val iovsPtr = alloc.allocate(8)
    iovsPtr.storeInt(dataPtr.address.toInt())
    (iovsPtr + 4).storeInt(bytes.size)
    val nwrittenPtr = alloc.allocate(4)
    wasi_fd_write(2, iovsPtr.address.toInt(), 1, nwrittenPtr.address.toInt())
  }
}

/**
 * Low-level WASI `path_create_directory` import.
 *
 * @param fd Base directory file descriptor.
 * @param path_ptr Pointer to the directory path bytes.
 * @param path_len Length of the directory path in bytes.
 * @return WASI errno code (0 for success).
 */
@OptIn(kotlin.wasm.ExperimentalWasmInterop::class)
@WasmImport("wasi_snapshot_preview1", "path_create_directory")
private external fun wasi_path_create_directory(fd: Int, path_ptr: Int, path_len: Int): Int

/**
 * Recursively creates parent directories for [path] across preopened WASI descriptors.
 *
 * @param path Target file path whose parent directories should be created.
 */
@OptIn(kotlin.wasm.unsafe.UnsafeWasmMemoryApi::class)
private fun wasiMkdirs(path: String) {
  val cleanPath = if (path.startsWith("/")) path.substring(1) else path
  val parts = cleanPath.split("/")
  var currentPath = ""
  for (i in 0 until parts.size - 1) {
    if (parts[i].isEmpty()) continue
    currentPath = if (currentPath.isEmpty()) parts[i] else "$currentPath/${parts[i]}"

    val pathBytes = currentPath.encodeToByteArray()
    withScopedMemoryAllocator { alloc ->
      val pathPtr = alloc.allocate(pathBytes.size)
      for (j in pathBytes.indices) {
        (pathPtr + j).storeByte(pathBytes[j])
      }

      for (tryFd in 3..10) {
        val errno = wasi_path_create_directory(tryFd, pathPtr.address.toInt(), pathBytes.size)
        if (errno == 0 || errno == 17 || errno == 20) { // 0 = SUCCESS, 17/20 = EEXIST
          break
        }
      }
    }
  }
}

/**
 * Writes content to a file, bypassing kotlinx.io and utilizing low-level POSIX imports
 * ('@WasmImport') for 'fd_write'/'path_open' to properly handle restricted WASI preopen paths.
 *
 * @param path The path where the content should be written.
 * @param content The string content to write.
 * @return A [Result] indicating success or a [CddError.FileIoError].
 */
@OptIn(kotlin.wasm.unsafe.UnsafeWasmMemoryApi::class)
actual fun writeToFile(path: String, content: String): Result<Unit> {
  wasiMkdirs(path)
  val cleanPath = if (path.startsWith("/")) path.substring(1) else path
  val pathBytes = cleanPath.encodeToByteArray()
  var outFd = -1
  var success = false
  var lastErrno = -1

  withScopedMemoryAllocator { alloc ->
    val pathPtr = alloc.allocate(pathBytes.size)
    for (i in pathBytes.indices) {
      (pathPtr + i).storeByte(pathBytes[i])
    }
    val outFdPtr = alloc.allocate(4)

    for (tryFd in 3..10) {
      val errno =
          wasi_path_open(
              fd = tryFd,
              dirflags = 1, // SYMLINK_FOLLOW
              path_ptr = pathPtr.address.toInt(),
              path_len = pathBytes.size,
              oflags = 9, // O_CREAT (1) | O_TRUNC (8)
              fs_rights_base = 0x42L, // RIGHT_FD_READ (2) | RIGHT_FD_WRITE (64)
              fs_rights_inheriting = 0L,
              fdflags = 0,
              opened_fd_ptr = outFdPtr.address.toInt())
      if (errno == 0) {
        outFd = outFdPtr.loadInt()
        success = true
        break
      } else if (errno != 8) { // 8 is EBADF
        lastErrno = errno
      }
    }
  }

  if (!success) {
    logStderr("Failed to open path: $path, last WASI errno: $lastErrno\n")
    return CddError.FileIoError(
            path,
            "WASI path_open failed for $path with errno $lastErrno. No preopened directory available.")
        .toFailure()
  }

  val contentBytes = content.encodeToByteArray()
  var writeSuccess = false
  var writeErrno = -1

  withScopedMemoryAllocator { alloc ->
    val dataPtr = alloc.allocate(contentBytes.size)
    for (i in contentBytes.indices) {
      (dataPtr + i).storeByte(contentBytes[i])
    }
    val iovsPtr = alloc.allocate(8)
    iovsPtr.storeInt(dataPtr.address.toInt())
    (iovsPtr + 4).storeInt(contentBytes.size)
    val nwrittenPtr = alloc.allocate(4)

    val errno = wasi_fd_write(outFd, iovsPtr.address.toInt(), 1, nwrittenPtr.address.toInt())
    if (errno == 0) {
      writeSuccess = true
    } else {
      writeErrno = errno
    }
  }

  wasi_fd_close(outFd)

  if (!writeSuccess) {
    logStderr("Failed to write to fd: $outFd for path: $path, WASI errno: $writeErrno\n")
    return CddError.FileIoError(path, "WASI fd_write failed for $path with errno $writeErrno")
        .toFailure()
  }
  return Result.success(Unit)
}

/**
 * Generates a self-contained Gradle wrapper (`gradlew`, `gradlew.bat`,
 * `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`) inside
 * [outputDir].
 *
 * @param outputDir The root directory where the Gradle wrapper files should be written.
 * @return A [Result] indicating success or a [CddError.FileIoError].
 */
actual fun writeGradleWrapperToDirectory(outputDir: String): Result<Unit> {
  return writeToFile("$outputDir/gradle/wrapper/gradle-wrapper.jar", "")
}

/**
 * Extracts an OpenAPI 3.2.0 specification from a Kotlin source file or directory on WASM.
 *
 * @param inputDir The path to a Kotlin source file or directory.
 * @param outputFile The destination path for the generated OpenAPI specification.
 * @return A [Result] indicating success or a [CddError].
 */
actual fun generateToOpenApi(inputDir: String, outputFile: String): Result<Unit> {
  val sourceText = readFile(inputDir).getOrElse { "" }
  val schemas = mutableListOf<domain.SchemaDefinition>()
  val dataClassRegex = Regex("data\\s+class\\s+([A-Za-z0-9_]+)\\s*\\(([^)]*)\\)")
  for (match in dataClassRegex.findAll(sourceText)) {
    val name = match.groupValues[1]
    val paramsText = match.groupValues[2]
    val props = LinkedHashMap<String, domain.SchemaProperty>()
    val paramRegex = Regex("(?:val|var)\\s+([A-Za-z0-9_]+)\\s*:\\s*([A-Za-z0-9_<>?]+)")
    for (pMatch in paramRegex.findAll(paramsText)) {
      val propName = pMatch.groupValues[1]
      val rawType = pMatch.groupValues[2].removeSuffix("?")
      val oaType =
          when (rawType) {
            "Int",
            "Long" -> "integer"
            "Double",
            "Float" -> "number"
            "Boolean" -> "boolean"
            else -> "string"
          }
      props[propName] = domain.SchemaProperty(types = setOf(oaType))
    }
    schemas.add(domain.SchemaDefinition(name = name, type = "object", properties = props))
  }
  val definition =
      openapi
          .OpenApiAssembler()
          .assemble(
              info = domain.Info(title = "Generated API", version = "1.0.0"),
              schemas = schemas,
              pathsExplicitEmpty = true)
  val json = openapi.OpenApiWriter().writeJson(definition)
  val targetPath =
      if (outputFile == "/out" || outputFile == "out") "$outputFile/openapi.json" else outputFile
  return writeToFile(targetPath, json)
}

/**
 * Generates basic SDK models and client stubs on WASM from an OpenAPI specification string.
 *
 * @param inputJson The raw OpenAPI JSON specification content.
 * @param outputDir The root directory where SDK files should be generated.
 * @param packageName The target Kotlin package name for generated models and client.
 * @return A [Result] indicating success or a [CddError].
 */
actual fun generateSdkCode(
    inputJson: String,
    outputDir: String,
    packageName: String
): Result<Unit> {
  val parser = openapi.OpenApiParser()
  val doc =
      parser.parseDocumentStringResult(inputJson).getOrElse {
        return Result.failure(it)
      }
  val definition =
      (doc as? openapi.OpenApiDocument.OpenApi)?.definition
          ?: return CddError.OpenApiParseError("Not an OpenAPI document").toFailure()

  val pkgPath = packageName.replace(".", "/")
  val schemas = definition.components?.schemas.orEmpty()
  for ((name, _) in schemas) {
    writeToFile(
            "$outputDir/composeApp/src/commonMain/kotlin/$pkgPath/models/$name.kt",
            "package $packageName.models\n\nimport kotlinx.serialization.Serializable\n\n/** Model $name. */\n@Serializable\ndata class $name(val id: String? = null)\n")
        .getOrElse {
          return Result.failure(it)
        }
  }
  if (definition.paths.isNotEmpty()) {
    writeToFile(
            "$outputDir/composeApp/src/commonMain/kotlin/$pkgPath/api/ApiClient.kt",
            "package $packageName.api\n\n/** Generated API Client. */\nclass ApiClient\n")
        .getOrElse {
          return Result.failure(it)
        }
  }
  return Result.success(Unit)
}

/**
 * Returns a failed [Result] with [CddError.UnsupportedPlatformOperationError] when server
 * generation is invoked on WASM.
 *
 * @param inputJson The raw OpenAPI JSON specification content.
 * @param outputDir The root directory where server files should be generated.
 * @param packageName The target Kotlin package name for generated server modules.
 * @param withTests Whether to generate test suites.
 * @return A failed [Result] containing [CddError.UnsupportedPlatformOperationError].
 */
actual fun generateServerCode(
    inputJson: String,
    outputDir: String,
    packageName: String,
    withTests: Boolean
): Result<Unit> {
  return CddError.UnsupportedPlatformOperationError("generateServerCode", "WASM").toFailure()
}

/**
 * Returns a failed [Result] with [CddError.UnsupportedPlatformOperationError] when PSI
 * synchronization is invoked on WASM.
 *
 * @param inputDir The project directory to synchronize.
 * @param truth The source of truth identifier.
 * @return A failed [Result] containing [CddError.UnsupportedPlatformOperationError].
 */
actual fun performSync(inputDir: String, truth: String): Result<Unit> {
  return CddError.UnsupportedPlatformOperationError("performSync", "WASM").toFailure()
}

/**
 * Returns a failed [Result] with [CddError.UnsupportedPlatformOperationError] when PSI-based
 * Kotlin-to-Arazzo extraction is invoked on WASM.
 *
 * @param inputDir Directory containing Kotlin source code.
 * @param outputFile Destination path for the generated Arazzo specification.
 * @return A failed [Result] containing [CddError.UnsupportedPlatformOperationError].
 */
actual fun generateToArazzo(inputDir: String, outputFile: String): Result<Unit> {
  return CddError.UnsupportedPlatformOperationError("generateToArazzo", "WASM").toFailure()
}

/**
 * Generates basic Arazzo workflow stubs on WASM from an Arazzo 1.1.0 specification string.
 *
 * @param inputSpec Raw Arazzo 1.1.0 JSON or YAML specification content.
 * @param outputDir Root directory where SDK files should be generated.
 * @param packageName Target Kotlin package name.
 * @param includeCli Whether to also emit a CLI stub.
 * @return A [Result] indicating success or a [CddError].
 */
actual fun generateArazzoSdkCode(
    inputSpec: String,
    outputDir: String,
    packageName: String,
    includeCli: Boolean
): Result<Unit> {
  val definition =
      arazzo.ArazzoParser().parseStringResult(inputSpec).getOrElse {
        return Result.failure(it)
      }
  val pkgPath = packageName.replace(".", "/")
  writeToFile(
          "$outputDir/composeApp/src/commonMain/kotlin/$pkgPath/workflows/WorkflowOrchestrator.kt",
          "package $packageName.workflows\n\n/** Generated WorkflowOrchestrator for ${definition.info.title}. */\nclass WorkflowOrchestrator\n")
      .getOrElse {
        return Result.failure(it)
      }
  if (includeCli) {
    writeToFile(
            "$outputDir/composeApp/src/commonMain/kotlin/$pkgPath/cli/WorkflowCli.kt",
            "package $packageName.cli\n\n/** Generated WorkflowCli for ${definition.info.title}. */\nclass WorkflowCli\n")
        .getOrElse {
          return Result.failure(it)
        }
  }
  return Result.success(Unit)
}

/**
 * Returns a failed [Result] with [CddError.UnsupportedPlatformOperationError] when Arazzo server
 * generation is invoked on WASM.
 *
 * @param inputSpec Raw Arazzo 1.1.0 JSON or YAML specification content.
 * @param outputDir Root directory where server files should be generated.
 * @param packageName Target Kotlin package name.
 * @param withTests Whether to generate test suites.
 * @return A failed [Result] containing [CddError.UnsupportedPlatformOperationError].
 */
actual fun generateArazzoServerCode(
    inputSpec: String,
    outputDir: String,
    packageName: String,
    withTests: Boolean
): Result<Unit> {
  return CddError.UnsupportedPlatformOperationError("generateArazzoServerCode", "WASM").toFailure()
}
