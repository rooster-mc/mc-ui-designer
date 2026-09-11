package dev.cypdashuhn.uidesigner.export

import dev.cypdashuhn.uidesigner.model.BlockPos
import dev.cypdashuhn.uidesigner.model.DesignJson
import dev.cypdashuhn.uidesigner.model.UiChest
import dev.cypdashuhn.uidesigner.model.UiRow
import kotlinx.serialization.encodeToString
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

object JsonExporter {
    private val WORLD_READABLE: FileAttribute<Set<PosixFilePermission>> =
        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-r--r--"))

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

    private fun normalized(chests: List<UiChest>): List<UiChest> {
        chests.forEach { it.requiredPosition() }
        return chests
            .sortedBy { it.requiredPosition() }
            .map { chest ->
                chest.copy(
                    name = chest.name?.takeIf { it.isNotBlank() },
                    content =
                        chest.content
                            .sortedBy { it.row }
                            .map { it.ordered() }
                            .filter { it.slots.isNotEmpty() },
                )
            }
    }

    private fun UiChest.requiredPosition(): BlockPos =
        requireNotNull(position) { "UiChest.position must be set before export" }

    private fun UiRow.ordered(): UiRow = copy(slots = slots.sortedBy { it.slot })

    private fun createTemp(directory: Path): Path =
        if (directory.fileSystem.supportedFileAttributeViews().contains("posix")) {
            Files.createTempFile(directory, "uidesigner-export-", ".tmp", WORLD_READABLE)
        } else {
            Files.createTempFile(directory, "uidesigner-export-", ".tmp")
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
