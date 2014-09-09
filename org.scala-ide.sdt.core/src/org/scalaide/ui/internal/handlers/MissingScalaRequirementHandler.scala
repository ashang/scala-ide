package org.scalaide.ui.internal.handlers

import org.eclipse.debug.core.IStatusHandler
import scala.reflect.runtime.universe
import org.eclipse.core.runtime.IStatus
import org.eclipse.debug.core.IStatusHandler
import org.eclipse.jface.dialogs.IDialogConstants
import org.eclipse.jface.dialogs.{ MessageDialog => MD }
import org.scalaide.ui.internal.preferences.CompilerSettings
import org.scalaide.util.internal.SettingConverterUtil
import org.scalaide.util.internal.Utils.WithAsInstanceOfOpt
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.compiler.IPresentationCompilerProxy
import org.scalaide.core.internal.project.Nature
import org.scalaide.core.IScalaProject
import org.scalaide.util.internal.ui.DisplayThread
import org.scalaide.util.internal.eclipse.SWTUtils
import org.scalaide.util.internal.eclipse.EclipseUtils

object MissingScalaRequirementHandler {

  /**
   * Status code indicating there is a missing compiler requirement on classpath.
   *  Linked to MissingScalaRequirementHandler via our statusHandlers extension (see plugin.xml)
   */
  final val STATUS_CODE_SCALA_MISSING = 1002

}

class MissingScalaRequirementHandler extends RichStatusHandler {

  override def doHandleStatus(status: IStatus, source: Object) = {
    val scalaPc = source.asInstanceOfOpt[IPresentationCompilerProxy]
    val shell = SWTUtils.getShell
    val title = "Add Scala library to project classpath?"

    val msg = status.getMessage()

    if (scalaPc.isDefined) {
      val project: IScalaProject = scalaPc.get.project
      val projectName = project.underlying.getName
      val message = s"There was an error initializing the Scala compiler: $msg \n\n The editor compiler will be restarted when the project is cleaned or the classpath is changed. Add the Scala library to the classpath of project $projectName?"

      if (!IScalaPlugin().headlessMode) {
          val dialog = new MD(
            shell,
            title,
            null,
            message,
            MD.WARNING,
            Array(IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL),
            1)
          dialog.open()
          val buttonId = dialog.getReturnCode()
          if (buttonId == IDialogConstants.OK_ID)
            EclipseUtils.withSafeRunner("Error occurred while trying to add Scala library to classpath.") {
              Nature.addScalaLibAndSave(project.underlying)
            }
      }
    }
  }

}
