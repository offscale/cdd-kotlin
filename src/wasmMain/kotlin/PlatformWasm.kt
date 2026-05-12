import kotlinx.io.writeString
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlin.wasm.WasmImport
import kotlin.wasm.unsafe.withScopedMemoryAllocator
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.Pointer

@OptIn(kotlin.wasm.ExperimentalWasmInterop::class)
@WasmImport("wasi_snapshot_preview1", "environ_sizes_get")
private external fun wasi_environ_sizes_get(environ_count_ptr: Int, environ_buf_size_ptr: Int): Int

@OptIn(kotlin.wasm.ExperimentalWasmInterop::class)
@WasmImport("wasi_snapshot_preview1", "environ_get")
private external fun wasi_environ_get(environ_ptr: Int, environ_buf_ptr: Int): Int

@OptIn(kotlin.wasm.unsafe.UnsafeWasmMemoryApi::class)
actual fun getEnvVar(name: String): String? {
    var count = 0
    var bufSize = 0
    withScopedMemoryAllocator { alloc ->
        val countPtr = alloc.allocate(4)
        val bufSizePtr = alloc.allocate(4)
        if (wasi_environ_sizes_get(countPtr.address.toInt(), bufSizePtr.address.toInt()) != 0) return null
        count = countPtr.loadInt()
        bufSize = bufSizePtr.loadInt()
    }
    if (count == 0) return null

    var res: String? = null
    withScopedMemoryAllocator { alloc ->
        val environPtr = alloc.allocate(count * 4)
        val environBufPtr = alloc.allocate(bufSize)
        if (wasi_environ_get(environPtr.address.toInt(), environBufPtr.address.toInt()) != 0) return null

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
 * @return The contents of the file as a String.
 */
@OptIn(kotlin.wasm.unsafe.UnsafeWasmMemoryApi::class)
actual fun readFile(path: String): String {
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
            val errno = wasi_path_open(
                fd = tryFd,
                dirflags = 1, // SYMLINK_FOLLOW
                path_ptr = pathPtr.address.toInt(),
                path_len = pathBytes.size,
                oflags = 0, // No O_CREAT
                fs_rights_base = 2L, // RIGHT_FD_READ (2)
                fs_rights_inheriting = 0L,
                fdflags = 0,
                opened_fd_ptr = inFdPtr.address.toInt()
            )
            if (errno == 0) {
                inFd = inFdPtr.loadInt()
                success = true
                break
            } else if (errno != 8) { // 8 is EBADF
                lastErrno = errno
            }
        }
        
        if (!success) {
            throw RuntimeException("Can't open $cleanPath for read: WASI errno $lastErrno")
        }
    }
    
    val bufferSize = 8192
    val resultBytes = mutableListOf<Byte>()
    
    withScopedMemoryAllocator { alloc ->
        val dataPtr = alloc.allocate(bufferSize)
        val iovsPtr = alloc.allocate(8)
        iovsPtr.storeInt(dataPtr.address.toInt())
        (iovsPtr + 4).storeInt(bufferSize)
        val nreadPtr = alloc.allocate(4)
        
        while (true) {
            val errno = wasi_fd_read(inFd, iovsPtr.address.toInt(), 1, nreadPtr.address.toInt())
            if (errno != 0) {
                wasi_fd_close(inFd)
                throw RuntimeException("WASI fd_read failed for $cleanPath with errno $errno")
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
    return resultBytes.toByteArray().decodeToString()
}

@OptIn(kotlin.wasm.ExperimentalWasmInterop::class)
@WasmImport("wasi_snapshot_preview1", "fd_read")
private external fun wasi_fd_read(fd: Int, iovs_ptr: Int, iovs_len: Int, nread_ptr: Int): Int

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

@OptIn(kotlin.wasm.ExperimentalWasmInterop::class)
@WasmImport("wasi_snapshot_preview1", "path_create_directory")
private external fun wasi_path_create_directory(fd: Int, path_ptr: Int, path_len: Int): Int

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
 */
@OptIn(kotlin.wasm.unsafe.UnsafeWasmMemoryApi::class)
actual fun writeToFile(path: String, content: String) {
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
