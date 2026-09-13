package dev.cypdashuhn.uidesigner.export

import kotlinx.serialization.encodeToString
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

object JsonExporter {
    private val WORLD_READABLE: Set<PosixFilePermission> =
        PosixFilePermissions.fromString("rw-r--r--")

    fun toJson(chests: List<UiChest>): String = DesignJson.encodeToString(orderedChests(chests))

    fun export(chests: List<UiChest>, target: Path) {
        val absolute = target.toAbsolutePath()
        val directory = absolute.parent
        Files.createDirectories(directory)
        val temp = createTemp(directory)
        try {
            Files.writeString(temp, toJson(chests))
            move(temp, absolute)
        } catch (e: Exception) {
            Files.deleteIfExists(temp)
            throw e
        }
    }

    private fun orderedChests(chests: List<UiChest>): List<UiChest> =
        chests
            .map { it to it.requiredPosition() }
            .sortedBy { (_, position) -> position }
            .map { (chest, _) ->
                chest.copy(
                    content =
                        chest.content
                            .sortedBy { it.row }
                            .map { it.ordered() }
                            .filter { it.slots.isNotEmpty() },
                )
            }

    private fun UiRow.ordered(): UiRow =
        copy(
            slots =
                slots
                    .sortedBy { it.slot }
                    .map { it.copy(name = it.name?.takeIf { name -> name.isNotBlank() }) },
        )

    private fun createTemp(directory: Path): Path {
        val temp = Files.createTempFile(directory, "uidesigner-export-", ".tmp")
        applyWorldReadablePermissions(temp)
        return temp
    }

    private fun applyWorldReadablePermissions(temp: Path) {
        if (!temp.fileSystem.supportedFileAttributeViews().contains("posix")) return
        try {
            Files.setPosixFilePermissions(temp, WORLD_READABLE)
        } catch (_: UnsupportedOperationException) {
            // Some filesystems report POSIX support but reject attribute writes; defaults are fine.
        }
    }

    private fun move(temp: Path, target: Path) {
        try {
            Files.move(
                temp,
                target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
