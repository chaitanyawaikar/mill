package mill.scalalib.bsp

import mill.define.Task
import mill._
import mill.api.internal.BspModuleApi._
import mill.api.internal.{BspBuildTarget, internal}

trait BspModule extends mill.define.Module with mill.api.internal.BspModuleApi {

  def bspDisplayName0: String = this.moduleSegments.render

  def bspDisplayName: String = bspDisplayName0 match {
    case "" => "root-module"
    case n => n
  }

  /** Use to fill most fields of `BuildTarget`. */
  @internal
  def bspBuildTarget: BspBuildTarget = BspBuildTarget(
    displayName = Some(bspDisplayName),
    baseDirectory = Some(moduleDir.toNIO),
    tags = Seq(Tag.Library, Tag.Application),
    languageIds = Seq(),
    canCompile = false,
    canTest = false,
    canRun = false,
    canDebug = false
  )

  /**
   * Use to populate the `BuildTarget.{dataKind,data}` fields.
   *
   * Mill specific implementations:
   * - [[JvmBuildTarget]]
   * - [[ScalaBuildTarget]]
   */
  @internal
  def bspBuildTargetData: Task[Option[(String, AnyRef)]] = Task.Anon { None }

}
