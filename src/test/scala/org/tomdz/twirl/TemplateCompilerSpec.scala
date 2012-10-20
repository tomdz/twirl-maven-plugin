/* Copyright 2012 Typesafe (http://www.typesafe.com)
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
 */

package org.tomdz.twirl

import org.specs2.mutable._
import org.tomdz.twirl.api.{Appendable, Format}

import java.io._
import java.nio.charset.Charset
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
object TemplateCompilerSpec extends Specification {

  import Helper._

  val sourceDir = new File(sys.props.get("twirl.spec.templateSourceDirectory").get).getCanonicalFile
  val generatedDir = new File(sys.props.get("twirl.spec.templateTargetDirectory").get).getCanonicalFile
  val generatedClasses = new File(sys.props.get("twirl.spec.templateClassesDirectory").get).getCanonicalFile
  scalax.file.Path(generatedDir).deleteRecursively()
  scalax.file.Path(generatedClasses).deleteRecursively()
  scalax.file.Path(generatedClasses).createDirectory()

  "The template compiler" should {
    "compile successfully" in {
      val helper = new CompilerHelper(sourceDir, generatedDir, generatedClasses)
      helper.compile[((String, List[String]) => (Int) => Html)]("real.scala.html", "html.real")("World", List("A", "B"))(4).toString.trim must beLike {
        case html => {
          if (html.contains("<h1>Hello World</h1>") &&
            html.contains("You have 2 items") &&
            html.contains("EA") &&
            html.contains("EB")) ok
          else ko
        }
      }

      helper.compile[(() => Html)]("static.scala.html", "html.static")().toString.trim must be_==(
        "<h1>It works</h1>")

      val hello = helper.compile[((String) => Html)]("hello.scala.html", "html.hello")("World").toString.trim

      hello must be_==(
        "<h1>Hello World!</h1><h1>xml</h1>")

      helper.compile[((collection.immutable.Set[String]) => Html)]("set.scala.html", "html.set")(Set("first", "second", "third")).toString.trim.replace("\n", "").replaceAll("\\s+", "") must be_==("firstsecondthird")

    }
    "fail compilation for error.scala.html" in {
      val helper = new CompilerHelper(sourceDir, generatedDir, generatedClasses)
      helper.compile[(() => Html)]("error.scala.html", "html.error") must throwA[CompilationError].like {
        case CompilationError(_, 2, 12) => ok
        case _ => ko
      }
    }

  }

}

object Helper {

  case class Html(text: String) extends Appendable[Html] {
    val buffer = new StringBuilder(text)

    def +(other: Html) = {
      buffer.append(other.buffer)
      this
    }

    override def toString = buffer.toString
  }

  object HtmlFormat extends Format[Html] {
    def raw(text: String) = Html(text)

    def escape(text: String) = Html(text.replace("<", "&lt;"))
  }

  case class CompilationError(message: String, line: Int, column: Int) extends RuntimeException(message)

  class CompilerHelper(sourceDir: File, generatedDir: File, generatedClasses: File) {

    import scala.tools.nsc.interactive.Global
    import scala.tools.nsc.util.Position
    import scala.tools.nsc.Settings
    import scala.tools.nsc.reporters.ConsoleReporter
    import org.tomdz.twirl.TwirlCompiler

    import java.net._
    import java.util.jar.JarFile

    val templateCompiler = TwirlCompiler

    val classloader = new URLClassLoader(Array(generatedClasses.toURI.toURL), templateCompiler.getClass.getClassLoader)

    val compiledDir = new File(sys.props.get("twirl.spec.compiledDirectory").get).getCanonicalFile
    val testCompiledDir = new File(sys.props.get("twirl.spec.testCompiledDirectory").get).getCanonicalFile

    val compiler = {

      class TryOption[E <: Exception] {
        def apply[A](f: => A)(implicit m: Manifest[E]) = {
          try { Some(f) } catch { case x if (m.erasure.isInstance(x)) => None }
        }
      }
      object TryOption { def apply[E <: Exception] = new TryOption[E] }

      def additionalClassPathEntry: Seq[String] =
        Class.forName("org.tomdz.twirl.api.Format").getClassLoader.asInstanceOf[URLClassLoader].getURLs.map(_.getFile).map(_.toString)

      // need to check for classpath manifest entries which the surefire plugin uses for forked mode
      val manifestClassPathEntries =
        additionalClassPathEntry.flatMap(entry => TryOption[IOException]{new JarFile(entry)})
                                .flatMap(jarFile => {
          val baseUrl = new File(jarFile.getName).toURL
          val jarManifest = jarFile.getManifest
          val attrs = jarManifest.getMainAttributes
          Option(attrs.getValue("Class-Path")).map(_.split("\\s")).flatten.map(newEntry => new URL(baseUrl, newEntry).toString)
        })

      val settings = new Settings
      val scalaObjectSource = Class.forName("scala.ScalaObject").getProtectionDomain.getCodeSource

      // is null in Eclipse/OSGI but luckily we don't need it there
      if (scalaObjectSource != null) {
        val compilerPath = Class.forName("scala.tools.nsc.Interpreter").getProtectionDomain.getCodeSource.getLocation
        val libPath = scalaObjectSource.getLocation
        val pathList = List(compilerPath, libPath, compiledDir.getAbsolutePath, testCompiledDir.getAbsolutePath)
        val origBootclasspath = settings.bootclasspath.value
        val fullClassPath = ((origBootclasspath :: pathList) ::: additionalClassPathEntry.toList ::: manifestClassPathEntries.toList) map (_.toString)

        settings.bootclasspath.value = fullClassPath mkString File.pathSeparator
        settings.outdir.value = generatedClasses.getAbsolutePath
      }

      val compiler = new Global(settings, new ConsoleReporter(settings) {
        override def printMessage(pos: Position, msg: String) = {
          throw CompilationError(msg, pos.line, pos.point)
        }
      })

      new compiler.Run

      compiler
    }

    def compile[T](templateName: String, className: String): T = {
      val templateFile = new File(sourceDir, templateName)
      val Some(generated) = templateCompiler.compile(templateFile, sourceDir, generatedDir,
        "org.tomdz.twirl.Helper.Html", "org.tomdz.twirl.Helper.HtmlFormat", Charset.forName("UTF-8"))

      val mapper = GeneratedSource(generated)

      val run = new compiler.Run

      try {
        run.compile(List(generated.getAbsolutePath))
      } catch {
        case CompilationError(msg, line, column) => throw CompilationError(
          msg, mapper.mapLine(line), mapper.mapPosition(column))
      }

      val t = classloader.loadClass(className + "$").getDeclaredField("MODULE$").get(null)

      t.getClass.getDeclaredMethod("f").invoke(t).asInstanceOf[T]
    }
  }

}
