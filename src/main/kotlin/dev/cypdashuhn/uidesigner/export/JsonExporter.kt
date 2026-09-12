package dev.cypdashuhn.uidesigner.export

import dev.rooster.region.BlockPos
import kotlinx.serialization.encodeToString
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

// TODO: Is this file optionally windows compatible? if not turn it into a windows compatible one.
object JsonExporter {
    private val WORLD_READABLE: Set<PosixFilePermission> =
        PosixFilePermissions.fromString("rw-r--r--")

    fun toJson(chests: List<UiChest>): String = DesignJson.encodeToString(normalized(chests))

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

    // TODO: Normalized by what? name undescriptive
    private fun normalized(chests: List<UiChest>): List<UiChest> =
        chests
            .map { it to it.requiredPosition() }
            .sortedBy { (_, position) -> position }
            .map { (chest, _) ->
                chest.copy(
                    name = chest.name?.takeIf { it.isNotBlank() },
                    content =
                        chest.content
                            .sortedBy { it.row }
                            .map { it.ordered() }
                            .filter { it.slots.isNotEmpty() },
                )
            }

    private fun UiChest.requiredPosition(): BlockPos =
        requireNotNull(position) { "UiChest.position must be set before export" }

    private fun UiRow.ordered(): UiRow =
        copy(
            slots =
                slots
                    .sortedBy { it.slot }
                    .map { it.copy(name = it.name?.takeIf { name -> name.isNotBlank() }) },
        )

    private fun createTemp(directory: Path): Path {
        val temp = Files.createTempFile(directory, "uidesigner-export-", ".tmp")
        if (directory.fileSystem.supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(temp, WORLD_READABLE)
        }
        return temp
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
