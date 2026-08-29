@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.deletesignaturechecksums

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import java.io.File

private val signatureChecksumSuffixes = listOf(
    ".asc.md5",
    ".asc.sha1",
    ".asc.sha256",
    ".asc.sha512"
)

// GPG signature files are already a cryptographic proof of integrity/authenticity, so checksums *of* them
// are redundant - deleting them shrinks the published file count without weakening what Central verifies.
@Suppress("unused")
class DeleteSignatureChecksumsPlugin: Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        tasks.withType(PublishToMavenRepository::class.java).configureEach { publishTask ->
            publishTask.doLast {
                val repositoryUrl = publishTask.repository.url
                if (repositoryUrl.scheme != "file") return@doLast
                val repositoryDir = File(repositoryUrl)
                if (!repositoryDir.isDirectory) return@doLast
                repositoryDir.walkTopDown()
                    .filter { file -> file.isFile && signatureChecksumSuffixes.any { file.name.endsWith(it) } }
                    .forEach { it.delete() }
            }
        }
    }
}
