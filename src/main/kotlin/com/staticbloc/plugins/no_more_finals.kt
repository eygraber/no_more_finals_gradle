package com.staticbloc.plugins

import com.android.build.api.transform.*
import javassist.ClassPool
import javassist.CtClass
import javassist.Modifier
import org.gradle.api.Project
import java.io.File
import java.io.FileInputStream
import java.lang.reflect.Modifier as ReflectModifier

open class NoMoreFinalsExtension {
  open var classes: Array<String>? = null
  open var excludedClasses: Array<String>? = null
  open var packages: Array<String>? = null
  open var excludedPackages: Array<String>? = null
  open var includeSubpackages = false
}

class NoMoreFinalsTransform(private val project: Project) : Transform() {
  private val pool = ClassPool.getDefault()
  private val extension = project.extensions.getByName("no_more_finals") as NoMoreFinalsExtension

  override fun transform(invocation: TransformInvocation) {
    val directoryInput: DirectoryInput = invocation.inputs.first().directoryInputs.first()
    val output: File = invocation.outputProvider.getContentLocation(directoryInput.name,
        setOf(QualifiedContent.DefaultContentType.CLASSES),
        setOf(QualifiedContent.Scope.PROJECT),
            Format.DIRECTORY)

    output.deleteRecursively()
    output.mkdirs()

    project.logger.info("Output: $output")

    invocation.inputs.forEach {
      it.directoryInputs.forEach {
        project.logger.info("Input: ${it.file.absolutePath}")
        traverseInputs(it.file, output) {
          val packageName = it.packageName
          val fqnName = it.name

          val isExcluded = extension.excludedClasses?.contains(fqnName) ?: false
          if(isExcluded) {
            return@traverseInputs false
          }

          if(extension.includeSubpackages) {
            extension.packages?.orEmpty()?.forEach packages@ {
              if(extension.excludedPackages?.contains(it) ?: false) {
                return@packages
              }

              if(packageName.startsWith(it)) {
                return@traverseInputs true
              }
            }
          }
          else {
            val isPackageIncluded = extension.packages?.contains(packageName) ?: false
            if(isPackageIncluded) {
              return@traverseInputs true
            }
          }

          val isClassIncluded = extension.classes?.contains(fqnName) ?: false
          if(isClassIncluded) {
            return@traverseInputs true
          }

          false
        }
      }
    }
  }

  private fun traverseInputs(input: File, output: File, transformFilter: (CtClass) -> Boolean) {
    input.walkTopDown().asSequence().forEach {
      fun copyFileToOutput(output: File, path: String, data: ByteArray) {
        val file = File(output, path)
        project.logger.info("Copy file to: $file")
        file.parentFile?.mkdirs()
        file.writeBytes(data)
      }

      val path = it.relativeTo(input).path
      if(it.isDirectory) {
        val copyTo = File(output, path)
        project.logger.info("Copy directory to: $copyTo")
      }
      else if(it.extension != "class") {
        copyFileToOutput(output, path, File(input, path).readBytes())
      }
      else {
        with(FileInputStream(it)) {
          try {
            val clazz = pool.makeClass(this)
            if(transformFilter(clazz)) {
              clazz.stripFinal()
              project.logger.quiet("Transformed class: ${clazz.name}")
              copyFileToOutput(output, path, clazz.toBytecode())
            }
            else {
              project.logger.quiet("Skipped transforming class: ${clazz.name}")
              copyFileToOutput(output, path, clazz.toBytecode())
            }
          }
          catch(e: Exception) {
            project.logger.warn("There was an error processing $input...treating like a regular file")
            copyFileToOutput(output, path, File(input, path).readBytes())
          }
        }
      }
    }
  }

  private fun CtClass.stripFinal() {
    defrost()
    stripFinalFromClass()
    stripPublicFinalMethods()
    stopPruning(true)
  }

  private fun CtClass.stripPublicFinalMethods() {
    declaredMethods.forEach {
      if (java.lang.reflect.Modifier.isPublic(it.modifiers) && java.lang.reflect.Modifier.isFinal(it.modifiers)) {
        it.modifiers = Modifier.clear(it.modifiers, java.lang.reflect.Modifier.FINAL)
        project.logger.info("$name#${it.name} - Stripped final")
      }
    }
  }

  private fun CtClass.stripFinalFromClass() {
    if (java.lang.reflect.Modifier.isFinal(modifiers)) {
      modifiers = Modifier.clear(modifiers, java.lang.reflect.Modifier.FINAL)
      project.logger.info("$name- Stripped final")
    }
  }

  override fun getName() = "NoMoreFinals"

  override fun getInputTypes() = setOf(QualifiedContent.DefaultContentType.CLASSES)

  override fun getScopes() = setOf(QualifiedContent.Scope.PROJECT)

  override fun isIncremental() = false
}
