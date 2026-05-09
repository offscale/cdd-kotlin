import kotlinx.io.writeString
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlin.wasm.WasmImport
import kotlin.wasm.unsafe.withScopedMemoryAllocator
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi

actual fun getEnvVar(name: String): String? = null

/**
 * Reads a file using kotlinx.io.
 * 
 * @param path The path of the file to read.
 * @return The contents of the file as a String.
 */
actual fun readFile(path: String): String {
    try {
        val p = Path(path)
        val source = SystemFileSystem.source(p).buffered()
        val text = source.readString()
        source.close()
        return text
    } catch (e: Throwable) {
        throw e
    }
}

@OptIn(kotlin.wasm.ExperimentalWasmInterop::class)
@WasmImport("wasi_snapshot_preview1", "path_open")
private external fun wasi_path_open(
    fd: Int, dirflags: Int, path_ptr: Int, path_len: Int,
    oflags: Int, fs_rights_base: Long, fs_rights_inheriting: Long,
    fdflags: Int, opened_fd_ptr: Int
): Int

@OptIn(kotlin.wasm.ExperimentalWasmInterop::class)
@WasmImport("wasi_snapshot_preview1", "fd_write")
private external fun wasi_fd_write(fd: Int, iovs_ptr: Int, iovs_len: Int, nwritten_ptr: Int): Int

@OptIn(kotlin.wasm.ExperimentalWasmInterop::class)
@WasmImport("wasi_snapshot_preview1", "fd_close")
private external fun wasi_fd_close(fd: Int): Int

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
 * Writes content to a file, bypassing kotlinx.io and utilizing low-level POSIX imports 
 * ('@WasmImport') for 'fd_write'/'path_open' to properly handle restricted WASI preopen paths.
 * 
 * @param path The path where the content should be written.
 * @param content The string content to write.
 */
@OptIn(kotlin.wasm.unsafe.UnsafeWasmMemoryApi::class)
actual fun writeToFile(path: String, content: String) {
    val pathBytes = path.encodeToByteArray()
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
            val errno = wasi_path_open(
                fd = tryFd,
                dirflags = 1, // SYMLINK_FOLLOW
                path_ptr = pathPtr.address.toInt(),
                path_len = pathBytes.size,
                oflags = 9, // O_CREAT (1) | O_TRUNC (8)
                fs_rights_base = 0x42L, // RIGHT_FD_READ (2) | RIGHT_FD_WRITE (64)
                fs_rights_inheriting = 0L,
                fdflags = 0,
                opened_fd_ptr = outFdPtr.address.toInt()
            )
            if (errno == 0) {
                outFd = outFdPtr.loadInt()
                success = true
                break
            } else if (errno != 8) { // 8 is EBADF
                lastErrno = errno
            }
        }
        
        if (!success) {
            logStderr("Failed to open path: $path, last WASI errno: $lastErrno\n")
            throw RuntimeException("WASI path_open failed for $path with errno $lastErrno. No preopened directory available.")
        }
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
        throw RuntimeException("WASI fd_write failed for $path with errno $writeErrno")
    }
}
