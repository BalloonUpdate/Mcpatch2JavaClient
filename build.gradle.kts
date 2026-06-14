import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Type
import java.io.File
import java.security.SecureRandom
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

fun getVersionName(tagName: String) = if(tagName.startsWith("v")) tagName.substring(1) else tagName
val gitTagName: String? get() = Regex("(?<=refs/tags/).*").find(System.getenv("GITHUB_REF") ?: "")?.value
val gitCommitSha: String? get() = System.getenv("GITHUB_SHA") ?: null
val debugVersion: String get() = System.getenv("DBG_VERSION") ?: "4.0.0"

group = "com.github.balloonupdate"
version = gitTagName?.run { getVersionName(this) } ?: debugVersion

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.ow2.asm:asm:9.7")
    }
}

plugins {
    id("java")
    id("com.gradleup.shadow") version "8.3.6"
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("-XDstringConcat=inline"))
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.formdev:flatlaf:2.6")
    implementation("com.formdev:flatlaf-intellij-themes:2.6")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.github.lookfirst:sardine:5.12")
    implementation("org.json:json:20231013")
    implementation("org.yaml:snakeyaml:2.0")
    implementation("commons-codec:commons-codec:1.18.0")
}

// =====================================================================
// Phase 1: ShadowJar — Fat JAR packaging
// =====================================================================
tasks.withType<ShadowJar> {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    archiveBaseName = "Mcpatch2JavaClient"
    archiveClassifier.set("shadow")

    // Exclude classes that require missing runtime dependencies
    // These are not used at runtime and cause ZKM FATAL errors
    exclude("com/github/sardine/ant/**")  // requires org.apache.tools.ant

    manifest {
        attributes("Version" to archiveVersion.get())
        attributes("Git-Commit" to (gitCommitSha ?: ""))
        attributes("Main-Class" to "entry.Boot")
        attributes("Premain-Class" to "entry.Boot")
    }
}

// =====================================================================
// Phase 2: L8 ASM String Encryption — AES-256-CBC per-string encryption
// =====================================================================
val encryptStrings by tasks.registering {
    dependsOn("shadowJar")
    group = "obfuscation"
    description = "L8 ASM string encryption: encrypt all string literals with AES-256-CBC"

    doLast {
        val shadowJar = tasks.shadowJar.get().archiveFile.get().asFile
        if (!shadowJar.exists()) throw GradleException("Shadow JAR not found: $shadowJar")

        val masterKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val masterKeyB64 = Base64.getEncoder().encodeToString(masterKey)

        // Generate _PART_A / _PART_B for key splitting
        val partA = IntArray(4) { SecureRandom().nextInt() }
        val partB = IntArray(4) { idx ->
            val combined = (masterKey[idx].toInt() xor masterKey[idx + 4].toInt()
                xor masterKey[idx + 8].toInt() xor masterKey[idx + 12].toInt()
                xor masterKey[idx + 16].toInt() xor masterKey[idx + 20].toInt()
                xor masterKey[idx + 24].toInt() xor masterKey[idx + 28].toInt())
            partA[idx] xor combined
        }

        val tmpDir = File(shadowJar.parentFile, "encrypt-tmp")
        tmpDir.deleteRecursively()
        tmpDir.mkdirs()

        ant.withGroovyBuilder {
            "unzip"("src" to shadowJar.absolutePath, "dest" to tmpDir.absolutePath)
        }

        var encryptedCount = 0

        tmpDir.walkTopDown().filter { it.name.endsWith(".class") }.forEach { classFile ->
            val relativePath = classFile.relativeTo(tmpDir).path.replace('\\', '/')
            val className = relativePath.removeSuffix(".class").replace('/', '.')

            if (className == "entry.StringDecryptor" || className == "module-info") return@forEach

            try {
                val bytes = classFile.readBytes()
                val cr = ClassReader(bytes)
                val cw = ClassWriter(cr, ClassWriter.COMPUTE_MAXS)
                var classModified = false

                cr.accept(object : org.objectweb.asm.ClassVisitor(Opcodes.ASM9, cw) {
                    // Strip annotations that reference missing classes (causes ZKM FATAL)
                    override fun visitAnnotation(descriptor: String?, visible: Boolean): org.objectweb.asm.AnnotationVisitor? {
                        if (descriptor != null) {
                            // Strip animal-sniffer, JetBrains @NotNull, and other problematic annotations
                            val desc = descriptor.lowercase()
                            if (desc.contains("animalsniffer") || desc.contains("ignorejrerequirement") ||
                                desc.contains("notnull") || desc.contains("nullable") ||
                                desc.contains("contract") || desc.contains("language")) {
                                return null // skip this annotation
                            }
                        }
                        return super.visitAnnotation(descriptor, visible)
                    }

                    override fun visitMethod(
                        access: Int, name: String, descriptor: String,
                        signature: String?, exceptions: Array<out String>?
                    ): MethodVisitor? {
                        val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                        return object : MethodVisitor(Opcodes.ASM9, mv) {
                            override fun visitAnnotation(descriptor: String?, visible: Boolean): org.objectweb.asm.AnnotationVisitor? {
                                if (descriptor != null) {
                                    val desc = descriptor.lowercase()
                                    if (desc.contains("animalsniffer") || desc.contains("ignorejrerequirement") ||
                                        desc.contains("notnull") || desc.contains("nullable") ||
                                        desc.contains("contract") || desc.contains("language")) {
                                        return null
                                    }
                                }
                                return super.visitAnnotation(descriptor, visible)
                            }
                            override fun visitLdcInsn(value: Any?) {
                                if (value is String && value.isNotEmpty() && value.length < 8000) {
                                    if (value.startsWith("/") || value == "UTF-8") {
                                        super.visitLdcInsn(value)
                                        return
                                    }
                                    try {
                                        val plainBytes = value.toByteArray(Charsets.UTF_8)
                                        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
                                        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                                        cipher.init(Cipher.ENCRYPT_MODE,
                                            SecretKeySpec(masterKey, "AES"),
                                            IvParameterSpec(iv))
                                        val ciphertext = cipher.doFinal(plainBytes)

                                        val mac = Mac.getInstance("HmacSHA256")
                                        mac.init(SecretKeySpec(masterKey, "HmacSHA256"))
                                        val hmac = mac.doFinal(ciphertext)

                                        val combined = ByteArray(iv.size + hmac.size + ciphertext.size)
                                        System.arraycopy(iv, 0, combined, 0, iv.size)
                                        System.arraycopy(hmac, 0, combined, iv.size, hmac.size)
                                        System.arraycopy(ciphertext, 0, combined, iv.size + hmac.size, ciphertext.size)

                                        val encrypted = Base64.getEncoder().encodeToString(combined)

                                        super.visitLdcInsn(encrypted)
                                        super.visitMethodInsn(
                                            Opcodes.INVOKESTATIC,
                                            "entry/StringDecryptor",
                                            "decrypt",
                                            "(Ljava/lang/String;)Ljava/lang/String;",
                                            false
                                        )
                                        classModified = true
                                        encryptedCount++
                                    } catch (e: Exception) {
                                        super.visitLdcInsn(value)
                                    }
                                } else {
                                    super.visitLdcInsn(value)
                                }
                            }
                        }
                    }
                }, 0)

                if (classModified) {
                    classFile.writeBytes(cw.toByteArray())
                }
            } catch (e: Exception) {
                // Skip unparseable classes
            }
        }

        // Patch StringDecryptor._KEY_B64 with the master key
        val sdFile = File(tmpDir, "entry/StringDecryptor.class")
        if (sdFile.exists()) {
            val bytes = sdFile.readBytes()
            val cr = ClassReader(bytes)
            val cw = ClassWriter(cr, ClassWriter.COMPUTE_MAXS)

            cr.accept(object : org.objectweb.asm.ClassVisitor(Opcodes.ASM9, cw) {
                override fun visitMethod(
                    access: Int, name: String, descriptor: String,
                    signature: String?, exceptions: Array<out String>?
                ): MethodVisitor? {
                    val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                    return object : MethodVisitor(Opcodes.ASM9, mv) {
                        override fun visitLdcInsn(value: Any?) {
                            if (value is String && value == "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=") {
                                super.visitLdcInsn(masterKeyB64)
                            } else {
                                super.visitLdcInsn(value)
                            }
                        }
                    }
                }
            }, 0)
            sdFile.writeBytes(cw.toByteArray())

            // Also patch _PART_A and _PART_B in <clinit>
            val sdBytes = sdFile.readBytes()
            val sdCr = ClassReader(sdBytes)
            val sdCw = ClassWriter(sdCr, ClassWriter.COMPUTE_MAXS)

            sdCr.accept(object : org.objectweb.asm.ClassVisitor(Opcodes.ASM9, sdCw) {
                override fun visitMethod(
                    access: Int, name: String, descriptor: String,
                    signature: String?, exceptions: Array<out String>?
                ): MethodVisitor? {
                    val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                    if (name == "<clinit>") {
                        return object : MethodVisitor(Opcodes.ASM9, mv) {
                            private var intIdx = 0
                            override fun visitIntInsn(opcode: Int, operand: Int) {
                                if (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) {
                                    if (intIdx < 4) {
                                        super.visitIntInsn(Opcodes.SIPUSH, partA[intIdx])
                                    } else if (intIdx < 8) {
                                        super.visitIntInsn(Opcodes.SIPUSH, partB[intIdx - 4])
                                    } else {
                                        super.visitIntInsn(opcode, operand)
                                    }
                                    intIdx++
                                } else {
                                    super.visitIntInsn(opcode, operand)
                                }
                            }
                        }
                    }
                    return mv
                }
            }, 0)
            sdFile.writeBytes(sdCw.toByteArray())
        }

        // Repack JAR
        val outputJar = File(shadowJar.parentFile, "Mcpatch2JavaClient-${project.version}-encrypted.jar")
        if (outputJar.exists()) outputJar.delete()

        ant.withGroovyBuilder {
            "jar"("destfile" to outputJar.absolutePath, "basedir" to tmpDir.absolutePath)
        }

        shadowJar.delete()
        outputJar.copyTo(shadowJar)
        outputJar.delete()

        tmpDir.deleteRecursively()

        logger.lifecycle("L8 String Encryption: $encryptedCount strings encrypted")
    }
}

// =====================================================================
// Phase 3: ZKM 21.0.0 Obfuscation
// =====================================================================
val obfuscate by tasks.registering {
    dependsOn(encryptStrings)
    group = "obfuscation"
    description = "ZKM 21.0.0 maximum strength obfuscation"

    doLast {
        val shadowJar = tasks.shadowJar.get().archiveFile.get().asFile
        if (!shadowJar.exists()) throw GradleException("Shadow JAR not found: $shadowJar")

        val zkmZip = File(project.rootDir, "../upload/ZKM-21.0.0-Cracked.zip")
        val zkmDir = File(project.buildDir, "zkm")

        if (!File(zkmDir, "ZKM.jar").exists()) {
            zkmDir.mkdirs()
            ant.withGroovyBuilder {
                "unzip"("src" to zkmZip.absolutePath, "dest" to zkmDir.absolutePath)
            }
        }

        val zkmJar = zkmDir.walkTopDown().firstOrNull { it.name == "ZKM.jar" }
            ?: throw GradleException("ZKM.jar not found in $zkmDir")

        val zkmScript = File(project.rootDir, "zkm_obfuscate.txt")
        if (!zkmScript.exists()) throw GradleException("ZKM script not found: ${zkmScript.absolutePath}")

        // Strategy: Only obfuscate application code, not third-party libraries
        // 1. Extract application classes from shadow JAR
        // 2. Create an "app-only" JAR for ZKM to process
        // 3. Run ZKM on the app-only JAR (with full fat JAR as classpath)
        // 4. Replace app classes in the shadow JAR with ZKM-obfuscated versions

        val appDir = File(project.buildDir, "app-classes")
        appDir.deleteRecursively()
        appDir.mkdirs()
        val libDir = File(project.buildDir, "lib-classes")
        libDir.deleteRecursively()
        libDir.mkdirs()

        // Application packages (our code that should be obfuscated)
        // NOTE: entry.Boot is EXCLUDED from ZKM processing because ZKM's exclude
        // directive doesn't preserve method names (even with exclude entry.Boot premain).
        // Boot is already L8-encrypted, so we just keep it as-is.
        val appPackages = listOf("com/github/balloonupdate/", "com/github/kasuminova/")

        // Split shadow JAR into app and lib classes
        ZipFile(shadowJar).use { zf ->
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.name.endsWith(".class")) continue
                val isApp = appPackages.any { entry.name.startsWith(it) }
                val targetDir = if (isApp) appDir else libDir
                val outFile = File(targetDir, entry.name)
                outFile.parentFile.mkdirs()
                zf.getInputStream(entry).copyTo(outFile.outputStream())
            }
        }

        // Create app-only JAR for ZKM
        val appJar = File(shadowJar.parentFile, "Mcpatch2JavaClient-${project.version}-app.jar")
        ant.withGroovyBuilder {
            "jar"("destfile" to appJar.absolutePath, "basedir" to appDir.absolutePath)
        }

        // Create lib JAR for classpath
        val libJar = File(shadowJar.parentFile, "Mcpatch2JavaClient-${project.version}-lib.jar")
        ant.withGroovyBuilder {
            "jar"("destfile" to libJar.absolutePath, "basedir" to libDir.absolutePath)
        }

        val inputJar = appJar.absolutePath
        val outputJar = File(shadowJar.parentFile, "Mcpatch2JavaClient-${project.version}-zkm.jar").absolutePath

        // Build ZKM classpath: copy dependency JARs to /tmp/zkm-cp/
        // ZKM has issues with long paths or certain characters in Gradle cache paths
        val zkmCpDir = File("/tmp/zkm-cp")
        zkmCpDir.deleteRecursively()
        zkmCpDir.mkdirs()
        val zkmClasspath = mutableListOf<String>()

        // Copy dependency JARs to /tmp/zkm-cp/ with simple names
        configurations.getByName("runtimeClasspath").resolve().forEach { jar ->
            val simpleName = jar.name
            val dest = File(zkmCpDir, simpleName)
            jar.copyTo(dest, overwrite = true)
            zkmClasspath.add(dest.absolutePath.replace("\\", "/"))
        }

        // Add JDK runtime
        val jrtFs = File(System.getProperty("java.home"), "lib/jrt-fs.jar")
        if (jrtFs.exists()) zkmClasspath.add(jrtFs.absolutePath.replace("\\", "/"))

        // Create minimal stub JAR for missing optional deps (Android, BouncyCastle, etc.)
        val stubDir = File(project.buildDir, "zkm-stubs")
        stubDir.deleteRecursively()
        stubDir.mkdirs()
        val stubClasses = listOf(
            "org/codehaus/mojo/animal_sniffer/IgnoreJRERequirement.class",
            "android/os/Build.class",
            "android/os/Build\$VERSION.class",
            "android/security/NetworkSecurityPolicy.class",
            "android/net/TrafficStats.class",
            "android/util/Log.class"
        )
        for (stubPath in stubClasses) {
            val stubFile = File(stubDir, stubPath)
            stubFile.parentFile.mkdirs()
            stubFile.writeBytes(_createStubClass(stubPath.removeSuffix(".class").replace('/', '.')))
        }
        val stubJar = File(project.buildDir, "zkm-stubs.jar")
        ant.withGroovyBuilder {
            "jar"("destfile" to stubJar.absolutePath, "basedir" to stubDir.absolutePath)
        }
        zkmClasspath.add(stubJar.absolutePath.replace("\\", "/"))

        val classpathStr = zkmClasspath.joinToString(" ") { cp ->
            "\"${cp}\""
        }

        val tmpScript = File(project.buildDir, "zkm_run.txt")
        tmpScript.parentFile.mkdirs()
        tmpScript.writeText(
            zkmScript.readText()
                .replace("__INPUT_JAR__", inputJar.replace("\\", "/"))
                .replace("__OUTPUT_JAR__", outputJar.replace("\\", "/"))
                .replace("__CLASSPATH_STATEMENTS__", "classpath $classpathStr;")
        )

        logger.lifecycle("Running ZKM obfuscation...")
        logger.lifecycle("  Input:  $inputJar")
        logger.lifecycle("  Output: $outputJar")
        logger.lifecycle("  Script: ${tmpScript.absolutePath}")

        val javaHome = System.getProperty("java.home")
        val javaBin = if (File(javaHome, "bin/java").exists()) File(javaHome, "bin/java").absolutePath
                       else "java"

        val process = ProcessBuilder(
            javaBin,
            "-Xmx2g",
            "-jar", zkmJar.absolutePath,
            tmpScript.absolutePath
        )
            .directory(zkmDir)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        process.waitFor(10, TimeUnit.MINUTES)

        logger.lifecycle("ZKM Output:\n$output")

        if (!File(outputJar).exists()) {
            throw GradleException("ZKM output JAR not found: $outputJar\nZKM Output:\n$output")
        }

        // Merge: combine ZKM-obfuscated app classes with unmodified lib classes
        logger.lifecycle("Merging obfuscated app classes with library classes...")
        val mergedJar = File(shadowJar.parentFile, "Mcpatch2JavaClient-${project.version}-merged.jar")

        // Read ZKM-obfuscated app classes
        val appEntries = mutableMapOf<String, ByteArray>()
        ZipFile(File(outputJar)).use { zf ->
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                appEntries[entry.name] = zf.getInputStream(entry).readBytes()
            }
        }

        // Read original lib classes
        val libEntries = mutableMapOf<String, ByteArray>()
        ZipFile(libJar).use { zf ->
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                libEntries[entry.name] = zf.getInputStream(entry).readBytes()
            }
        }

        // Also read non-class resources from original shadow JAR (including manifest)
        val resourceEntries = mutableMapOf<String, ByteArray>()
        var manifestData: ByteArray? = null
        ZipFile(shadowJar).use { zf ->
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name == "META-INF/MANIFEST.MF") {
                    manifestData = zf.getInputStream(entry).readBytes()
                } else if (!entry.name.endsWith(".class")) {
                    resourceEntries[entry.name] = zf.getInputStream(entry).readBytes()
                }
            }
        }

        // Write merged JAR: app (ZKM-obfuscated) + lib (unchanged) + resources
        // Deduplicate entries (prefer app entries over lib entries)
        val allEntries = mutableMapOf<String, ByteArray>()
        // First add lib entries (lower priority)
        for ((name, data) in libEntries) {
            allEntries[name] = data
        }
        // Then add resources (medium priority)
        for ((name, data) in resourceEntries) {
            allEntries[name] = data
        }
        // Finally add app entries (highest priority - ZKM-obfuscated)
        for ((name, data) in appEntries) {
            allEntries[name] = data
        }

        ZipOutputStream(mergedJar.outputStream()).use { zos ->
            // Find the Boot class in ZKM output by searching for Instrumentation reference
            // ZKM renames both the package and class name, so we search by content
            var bootClassName = "entry.Boot" // default fallback
            val instrBytes = "java/lang/instrument/Instrumentation".toByteArray()
            for ((name, data) in appEntries) {
                if (name.endsWith(".class") && data.indices.any { data.sliceArray(it until minOf(it + instrBytes.size, data.size)).contentEquals(instrBytes) }) {
                    bootClassName = name.removeSuffix(".class").replace('/', '.')
                    logger.lifecycle("  Found Boot class (ZKM-renamed): $bootClassName")
                    break
                }
            }

            val manifest = """
                |Manifest-Version: 1.0
                |Premain-Class: $bootClassName
                |Main-Class: $bootClassName
                |Can-Redefine-Classes: true
                |Can-Retransform-Classes: true
            """.trimMargin() + "\n"
            zos.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
            zos.write(manifest.toByteArray())
            zos.closeEntry()

            for ((name, data) in allEntries.toSortedMap()) {
                if (name.startsWith("META-INF/")) continue // skip old manifests
                zos.putNextEntry(ZipEntry(name))
                zos.write(data)
                zos.closeEntry()
            }
        }

        // Replace shadow JAR with merged JAR
        shadowJar.delete()
        mergedJar.copyTo(shadowJar)
        mergedJar.delete()
        File(outputJar).delete()
        appJar.delete()
        libJar.delete()

        logger.lifecycle("ZKM obfuscation + merge completed successfully")
        logger.lifecycle("  App classes (obfuscated): ${appEntries.size}")
        logger.lifecycle("  Lib classes (unchanged): ${libEntries.size}")
    }
}

// =====================================================================
// Phase 4: Post-ZKM — Inject class hashes into IntegrityChecker via ASM
// =====================================================================
val postZkm by tasks.registering {
    dependsOn(obfuscate)
    group = "obfuscation"
    description = "Post-ZKM: inject class SHA-256 hashes into IntegrityChecker"

    doLast {
        val shadowJar = tasks.shadowJar.get().archiveFile.get().asFile
        if (!shadowJar.exists()) throw GradleException("Shadow JAR not found: $shadowJar")

        logger.lifecycle("Post-ZKM: Computing class hashes and injecting into IntegrityChecker...")

        // Step 1: Read JAR entries and compute hashes of critical classes
        val entries = mutableMapOf<String, ByteArray>()
        val hashEntries = mutableMapOf<String, String>()

        ZipFile(shadowJar).use { zf ->
            val entriesEnum = zf.entries()
            while (entriesEnum.hasMoreElements()) {
                val entry = entriesEnum.nextElement()
                val bytes = zf.getInputStream(entry).readBytes()
                entries[entry.name] = bytes
            }
        }

        // Find IntegrityChecker class (may be renamed by ZKM)
        var icPath: String? = null
        var icData: ByteArray? = null

        for ((name, data) in entries) {
            if (!name.endsWith(".class")) continue

            // Find by characteristic strings (after ZKM, string literals are L8-encrypted,
            // but class structure patterns remain)
            // Look for: has _HASHES field, references to SHA-256, etc.
            // Strategy: find class with "_HASHES" or "IntegrityChecker" in its data
            val strData = String(data, Charsets.ISO_8859_1)

            // Check if this class contains the _HASHES field name
            // After ZKM, field names are obfuscated too, so we need another approach
            // Check for SHA-256 constant and MessageDigest reference
            if (strData.contains("SHA-256") && strData.contains("MessageDigest")) {
                // This is likely IntegrityChecker
                val hash = MessageDigest.getInstance("SHA-256").digest(data)
                val hashB64 = Base64.getEncoder().encodeToString(hash)
                hashEntries[name] = hashB64
                icPath = name
                icData = data
                logger.lifecycle("  Found IntegrityChecker: $name (hash: ${hashB64.take(16)}...)")
            }

            // Check for Boot class (contains premain + Instrumentation)
            if (strData.contains("premain") && strData.contains("java/lang/instrument/Instrumentation")) {
                val hash = MessageDigest.getInstance("SHA-256").digest(data)
                val hashB64 = Base64.getEncoder().encodeToString(hash)
                hashEntries[name] = hashB64
                logger.lifecycle("  Found Boot: $name (hash: ${hashB64.take(16)}...)")
            }

            // Check for StringDecryptor (contains AES/CBC)
            if (strData.contains("AES/CBC/PKCS5Padding") && strData.contains("decrypt")) {
                val hash = MessageDigest.getInstance("SHA-256").digest(data)
                val hashB64 = Base64.getEncoder().encodeToString(hash)
                hashEntries[name] = hashB64
                logger.lifecycle("  Found StringDecryptor: $name (hash: ${hashB64.take(16)}...)")
            }

            // Check for SecurityGuard (contains ManagementFactory)
            if (strData.contains("ManagementFactory") && strData.contains("InputArguments")) {
                val hash = MessageDigest.getInstance("SHA-256").digest(data)
                val hashB64 = Base64.getEncoder().encodeToString(hash)
                hashEntries[name] = hashB64
                logger.lifecycle("  Found SecurityGuard: $name (hash: ${hashB64.take(16)}...)")
            }
        }

        if (icPath == null || icData == null) {
            logger.lifecycle("  WARNING: IntegrityChecker not found, skipping hash injection")
            return@doLast
        }

        // Step 2: Build hash string
        // Format: "classPath1:sha256hash1,classPath2:sha256hash2,..."
        // Exclude IntegrityChecker itself (chicken-and-egg: we're modifying it)
        val filteredHashes = hashEntries.filter { (k, _) -> k != icPath }
        val hashString = filteredHashes.entries.joinToString(",") { (k, v) -> "$k:$v" }

        logger.lifecycle("  Hash table: ${filteredHashes.size} entries")
        logger.lifecycle("  Hash string length: ${hashString.length}")

        // Step 3: Use ASM to inject hashString into IntegrityChecker._HASHES field
        val cr = ClassReader(icData)
        val cw = ClassWriter(cr, ClassWriter.COMPUTE_MAXS)
        var injected = false

        cr.accept(object : org.objectweb.asm.ClassVisitor(Opcodes.ASM9, cw) {
            override fun visitMethod(
                access: Int, name: String, descriptor: String,
                signature: String?, exceptions: Array<out String>?
            ): MethodVisitor? {
                val mv = super.visitMethod(access, name, descriptor, signature, exceptions)

                // Look for <clinit> (static initializer) to inject hash
                if (name == "<clinit>") {
                    return object : MethodVisitor(Opcodes.ASM9, mv) {
                        private var foundPutStatic = false

                        override fun visitLdcInsn(value: Any?) {
                            // Replace the empty string "" with our hash string
                            if (value is String && value.isEmpty() && !foundPutStatic) {
                                super.visitLdcInsn(hashString)
                                foundPutStatic = true
                                injected = true
                                logger.lifecycle("  Injected hash table into <clinit>")
                            } else {
                                super.visitLdcInsn(value)
                            }
                        }
                    }
                }

                // Also try to find the _HASHES field assignment pattern
                // In the bytecode, _HASHES = "" becomes:
                //   ldc ""  →  putstatic IntegrityChecker._HASHES
                return object : MethodVisitor(Opcodes.ASM9, mv) {
                    override fun visitLdcInsn(value: Any?) {
                        if (value is String && value.isEmpty() && !injected) {
                            // This might be the _HASHES assignment
                            super.visitLdcInsn(hashString)
                            injected = true
                            logger.lifecycle("  Injected hash table via field assignment")
                        } else {
                            super.visitLdcInsn(value)
                        }
                    }
                }
            }
        }, 0)

        if (injected) {
            entries[icPath] = cw.toByteArray()
            logger.lifecycle("  Hash injection successful")
        } else {
            logger.lifecycle("  WARNING: Could not inject hash via ASM (no empty string found in <clinit>)")
            // Fallback: try direct binary patch
            // Find the last empty UTF-8 string in the constant pool and replace
            val patched = _patchConstantPoolString(icData, hashString)
            if (patched != null) {
                entries[icPath] = patched
                logger.lifecycle("  Hash injection via constant pool patch successful")
            } else {
                logger.lifecycle("  WARNING: All injection methods failed, skipping")
            }
        }

        // Step 4: Repack JAR
        val tmpJar = File(shadowJar.parentFile, "Mcpatch2JavaClient-${project.version}-postzkm.jar")
        ZipOutputStream(tmpJar.outputStream()).use { zos ->
            for ((name, data) in entries.toSortedMap()) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(data)
                zos.closeEntry()
            }
        }

        shadowJar.delete()
        tmpJar.copyTo(shadowJar)
        tmpJar.delete()

        logger.lifecycle("Post-ZKM processing completed successfully")
    }
}

/**
 * Fallback: Direct constant pool patching for hash injection
 * Finds the last empty UTF-8 entry in the constant pool and replaces it
 */
fun _patchConstantPoolString(classData: ByteArray, newValue: String): ByteArray? {
    val data = ByteArray(classData.size + 1024) // extra space for larger string
    System.arraycopy(classData, 0, data, 0, classData.size)
    val actualLen = classData.size

    // Parse class file to find empty UTF-8 constant pool entries
    var pos = 8 // skip magic, minor, major
    if (actualLen < pos + 2) return null

    val cpCount = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
    pos += 2

    val emptyUtf8Positions = mutableListOf<Int>()
    var cpIndex = 1
    var i = pos

    while (cpIndex < cpCount && i < actualLen - 2) {
        val tag = data[i].toInt() and 0xFF
        when (tag) {
            1 -> { // UTF-8
                val length = ((data[i + 1].toInt() and 0xFF) shl 8) or (data[i + 2].toInt() and 0xFF)
                if (length == 0) {
                    emptyUtf8Positions.add(i)
                }
                i += 3 + length
            }
            7, 8, 16, 19, 20 -> i += 3
            9, 10, 11, 12, 17, 18 -> i += 5
            15 -> i += 4
            6, 5 -> { i += 9; cpIndex++ } // double/long take 2 slots
            else -> i += 1
        }
        cpIndex++
    }

    if (emptyUtf8Positions.isEmpty()) return null

    // Replace the last empty UTF-8 entry with our hash string
    val offset = emptyUtf8Positions.last()
    val valueBytes = newValue.toByteArray(Charsets.UTF_8)
    val newEntry = ByteArray(3 + valueBytes.size)
    newEntry[0] = 1 // UTF-8 tag
    newEntry[1] = ((valueBytes.size shr 8) and 0xFF).toByte()
    newEntry[2] = (valueBytes.size and 0xFF).toByte()
    System.arraycopy(valueBytes, 0, newEntry, 3, valueBytes.size)

    // Build result: before + new entry + after
    val result = ByteArray(offset + newEntry.size + (actualLen - offset - 3))
    System.arraycopy(data, 0, result, 0, offset)
    System.arraycopy(newEntry, 0, result, offset, newEntry.size)
    System.arraycopy(data, offset + 3, result, offset + newEntry.size, actualLen - offset - 3)

    return result
}

// =====================================================================
// Final: copy to download directory
// =====================================================================
val finalizeJar by tasks.registering {
    dependsOn(postZkm)
    group = "build"
    description = "Copy final JAR to download directory"

    doLast {
        val shadowJar = tasks.shadowJar.get().archiveFile.get().asFile
        val downloadDir = File(project.rootDir, "../download")
        downloadDir.mkdirs()
        val finalJar = File(downloadDir, "Mcpatch2JavaClient-4.0.0-zkm.jar")
        shadowJar.copyTo(finalJar, overwrite = true)

        val sizeMB = String.format("%.1f", finalJar.length() / 1024.0 / 1024.0)
        logger.lifecycle("Final JAR: ${finalJar.absolutePath} (${sizeMB} MB)")
    }
}

tasks.build {
    dependsOn(finalizeJar)
}

/**
 * Create a minimal stub class file for ZKM classpath resolution.
 * Just enough for ZKM to parse - contains class declaration only.
 */
fun _createStubClass(className: String): ByteArray {
    val internalName = className.replace('.', '/')
    val cw = ClassWriter(0)
    cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null)
    val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
    mv.visitCode()
    mv.visitVarInsn(Opcodes.ALOAD, 0)
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
    mv.visitInsn(Opcodes.RETURN)
    mv.visitMaxs(1, 1)
    mv.visitEnd()
    cw.visitEnd()
    return cw.toByteArray()
}
