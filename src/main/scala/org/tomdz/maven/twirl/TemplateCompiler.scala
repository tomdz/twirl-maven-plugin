/* Copyright 2012 Typesafe (http://www.typesafe.com), Johannes Rudolph
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This is an adapted version of the Play20 sbt-plugin at
 * https://github.com/playframework/Play20/raw/217271a2d6834b2abefa8eff070ec680c7956a99/framework/src/sbt-plugin/src/main/scala/PlayCommands.scala
 */

package org.tomdz.maven.twirl

import java.io.File
import java.nio.charset.Charset
import org.apache.maven.plugin.MojoExecutionException
import scala.collection.JavaConverters._
import org.apache.maven.plugin.logging.Log

case class TemplateType(resultType: String, formatterType: String)

object TemplateCompiler {
  val templateTypes: PartialFunction[String, TemplateType] = Map(
    "html" -> TemplateType("org.tomdz.maven.twirl.api.Html", "org.tomdz.maven.twirl.api.HtmlFormat"),
    "txt"  -> TemplateType("org.tomdz.maven.twirl.api.Txt", "org.tomdz.maven.twirl.api.TxtFormat"),
    "xml"  -> TemplateType("org.tomdz.maven.twirl.api.Xml", "org.tomdz.maven.twirl.api.XmlFormat")
  )

  def compile(sourceDirectory: File,
              generatedDir: File,
              sourceCharset: Charset,
              additionalImports: java.util.List[String],
              log: Log) = {
    try {
      generatedDir.mkdirs

      cleanUp(generatedDir)

      val templates = collectTemplates(sourceDirectory)

      for ((templateFile, extension, TemplateType(resultType, formatterType)) <- templates) {
        val addImports = additionalImports.asScala.map("import " + _.replace("%format%", extension)).mkString("\n")
        TwirlCompiler.compile(templateFile,
                              sourceDirectory,
                              generatedDir,
                              resultType,
                              formatterType,
                              sourceCharset,
                              addImports,
                              targetFile => {
                                val skipChars = sourceDirectory.toString.length
                                log.info("Compiling twirl template ..." + templateFile.toString.substring(skipChars) +
                                         " to .../" + targetFile.getName)
                              })
      }

      findFiles(generatedDir, ".*\\.template\\.scala").map(_.getAbsoluteFile)

    } catch handleTemplateCompilationError
  }

  private def findFiles(directory: File, pattern: String) = {
    for {
      file <- directory.listFiles
      if file.isFile
      if file.getName.matches(pattern)
    } yield file
  }

  private def cleanUp(generatedDir: File) {
    findFiles(generatedDir, ".*\\.template\\.scala").foreach {
      GeneratedSource(_).sync()
    }
  }

  private def collectTemplates(sourceDirectory: File) = {
    findFiles(sourceDirectory, ".*\\.scala\\..*").flatMap { file =>
      val ext = file.getName.split('.').last
      if (templateTypes.isDefinedAt(ext)) Some(file, ext, templateTypes(ext))
      else None
    }
  }

  private val handleTemplateCompilationError: PartialFunction[Throwable, Nothing] = {
    case TemplateCompilationError(source, message, line, column) =>
      throw new MojoExecutionException("Error in file " + source.getAbsolutePath + " at line " + line + ", column " + column + ": " + message)
    case e => throw e
  }
}
