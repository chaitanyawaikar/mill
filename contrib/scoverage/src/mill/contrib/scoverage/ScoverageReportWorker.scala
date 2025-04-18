package mill.contrib.scoverage

import mill.Task
import mill.define.{TaskCtx, PathRef}
import mill.contrib.scoverage.api.ScoverageReportWorkerApi2
import mill.define.{Discover, ExternalModule, Worker}

import ScoverageReportWorker.ScoverageReportWorkerApiBridge
import ScoverageReportWorkerApi2.ReportType
import ScoverageReportWorkerApi2.{Logger => ApiLogger}
import ScoverageReportWorkerApi2.{Ctx => ApiCtx}

class ScoverageReportWorker extends AutoCloseable {
  private var scoverageClCache = Option.empty[(Long, ClassLoader)]

  def bridge(classpath: Seq[PathRef])(implicit ctx: TaskCtx): ScoverageReportWorkerApiBridge = {

    val classloaderSig = classpath.hashCode
    val cl = scoverageClCache match {
      case Some((sig, cl)) if sig == classloaderSig => cl
      case _ =>
        val toolsClassPath = classpath.map(_.path).toVector
        ctx.log.debug("Loading worker classes from\n" + toolsClassPath.mkString("\n"))
        val cl = mill.util.Jvm.createClassLoader(
          toolsClassPath,
          getClass.getClassLoader
        )
        scoverageClCache = Some((classloaderSig, cl))
        cl
    }

    val worker =
      cl
        .loadClass("mill.contrib.scoverage.worker.ScoverageReportWorkerImpl")
        .getDeclaredConstructor()
        .newInstance()
        .asInstanceOf[api.ScoverageReportWorkerApi2]

    def ctx0(implicit ctx: TaskCtx): ApiCtx = {
      val logger = new ApiLogger {
        def error(msg: String): Unit = ctx.log.error(msg)
        def warn(msg: String): Unit = ctx.log.warn(msg)
        def info(msg: String): Unit = ctx.log.info(msg)
        def debug(msg: String): Unit = ctx.log.debug(msg)
      }
      new ApiCtx {
        def log() = logger
        def dest() = ctx.dest.toNIO
      }
    }

    new ScoverageReportWorkerApiBridge {
      override def report(
          reportType: ReportType,
          sources: Seq[os.Path],
          dataDirs: Seq[os.Path],
          sourceRoot: os.Path
      )(implicit
          ctx: TaskCtx
      ): Unit = {
        worker.report(
          reportType,
          sources.map(_.toNIO).toArray,
          dataDirs.map(_.toNIO).toArray,
          sourceRoot.toNIO,
          ctx0
        )
      }
    }
  }

  override def close(): Unit = {
    scoverageClCache = None
  }
}

object ScoverageReportWorker extends ExternalModule {
  import ScoverageReportWorkerApi2.ReportType

  trait ScoverageReportWorkerApiBridge {
    def report(
        reportType: ReportType,
        sources: Seq[os.Path],
        dataDirs: Seq[os.Path],
        sourceRoot: os.Path
    )(implicit
        ctx: TaskCtx
    ): Unit
  }

  def scoverageReportWorker: Worker[ScoverageReportWorker] =
    Task.Worker { new ScoverageReportWorker() }
  lazy val millDiscover = Discover[this.type]
}
