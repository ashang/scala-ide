package org.scalaide.ui.internal.editor.outline

import org.eclipse.jface.action.IMenuManager
import org.eclipse.jface.action.Separator
import org.eclipse.jface.dialogs.Dialog
import org.eclipse.jface.dialogs.PopupDialog
import org.eclipse.jface.text.IInformationControl
import org.eclipse.jface.text.IInformationControlExtension
import org.eclipse.jface.text.IInformationControlExtension2
import org.eclipse.jface.viewers._
import org.eclipse.swt.SWT
import org.eclipse.swt.events._
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.widgets._
import org.eclipse.ui.texteditor.AbstractTextEditor

import org.scalaide.core.internal.ScalaPlugin
import org.scalaide.ui.internal.preferences.EditorPreferencePage
import org.scalaide.util.eclipse.SWTUtils._

/**
 * ScalaOutlineInformationControl is based on AbstractInformationControl and JavaOutlineInformationControl.
 * AbstractInformationControl has private members written against Java model, which makes impossible to use this class as a base class.
 * So, a big chunk of logic just translated to scala line by line.
 */
final class ScalaOutlineInformationControl(parent: Shell, shellStyle: Int, treeStyle: Int, commandId: String, editor: AbstractTextEditor)
    extends PopupDialog(parent, shellStyle, true, true, false, true, true, null, null)
    with IInformationControl
    with IInformationControlExtension
    with IInformationControlExtension2 {

  private var namePattern: String = ""
  private val contentProvider = new ScalaOutlineContentProvider
  protected def getId(): String = "org.scalaide.ui.internal.editor.outline.QuickOutline"
  private var filterText: Text = _

  create()

  setInfoText("")

  var treeViewer: TreeViewer = _

  def createTreeViewer(parent: org.eclipse.swt.widgets.Composite, style: Int): TreeViewer = {
    val tree = new Tree(parent, SWT.SINGLE | (style & ~SWT.MULTI))
    val gd = new GridData(GridData.FILL_BOTH)
    gd.heightHint = tree.getItemHeight() * 12
    tree.setLayoutData(gd)
    treeViewer = new TreeViewer(tree)
    treeViewer.setContentProvider(contentProvider)
    treeViewer.setLabelProvider(new ScalaOutlineLabelProvider)
    treeViewer.addFilter(new NameFilter())
    treeViewer.setAutoExpandLevel(AbstractTreeViewer.ALL_LEVELS)
    treeViewer
  }

  override def createDialogArea(parent: Composite): Control = {
    treeViewer = createTreeViewer(parent, treeStyle)
    val tree = treeViewer.getTree()
    tree.addKeyListener(new KeyListener() {
      def keyPressed(e: KeyEvent) = {
        if (e.character == 0x1B) // ESC
          dispose()
      }
      def keyReleased(e: KeyEvent) = {
        // do nothing
      }
    })

    tree.addSelectionListener(new SelectionListener() {
      def widgetSelected(e: SelectionEvent) = {
        // do nothing
      }
      def widgetDefaultSelected(e: SelectionEvent) = {
        gotoSelectedElement()
      }
    })

    tree.addMouseMoveListener(new MouseMoveListener() {
      var fLastItem: TreeItem = null
      def mouseMove(e: MouseEvent) = {
        if (tree.equals(e.getSource())) {
          val o = tree.getItem(new Point(e.x, e.y))
          if (fLastItem == null ^ o == null) {
            tree.setCursor(if (o eq null) null else tree.getDisplay.getSystemCursor(SWT.CURSOR_HAND))
          }
          o match {
            case ti: TreeItem =>
              val clientArea = tree.getClientArea()
              if (!o.equals(fLastItem)) {
                fLastItem = ti
                tree.setSelection(Array(fLastItem))
              } else if (e.y - clientArea.y < tree.getItemHeight() / 4) {
                // Scroll up
                val p = tree.toDisplay(e.x, e.y)
                val item = treeViewer.scrollUp(p.x, p.y)
                if (item.isInstanceOf[TreeItem]) {
                  fLastItem = item.asInstanceOf[TreeItem]
                  tree.setSelection(Array(fLastItem))
                }
              } else if (clientArea.y + clientArea.height - e.y < tree.getItemHeight() / 4) {
                // Scroll down
                val p = tree.toDisplay(e.x, e.y)
                val item = treeViewer.scrollDown(p.x, p.y)
                if (item.isInstanceOf[TreeItem]) {
                  fLastItem = item.asInstanceOf[TreeItem]
                  tree.setSelection(Array(fLastItem))
                }
              }
            case _ => fLastItem = null

          }
        }
      }
    })

    tree.addMouseListener(new MouseAdapter() {

      override def mouseUp(e: MouseEvent) = {

        if (tree.getSelectionCount() >= 1 && e.button == 1 && tree.equals(e.getSource())) {

          val o = tree.getItem(new Point(e.x, e.y))
          val selection = tree.getSelection()(0)
          if (selection.equals(o))
            gotoSelectedElement()
        }
      }
    })

    installFilter()

    treeViewer.getControl()
  }

  /** This is used to select the first match in the UI. */
  private var matchedNode: Option[Node] = None

  class NameFilter extends ViewerFilter {
    override def select(viewer: Viewer, parentElement: Object, element: Object): Boolean = {

      def nameMatch(name: String, pattern: String): Boolean =
        name.toLowerCase().contains(pattern.toLowerCase())

      def matchPattern(node: Any): Boolean = {
        node match {
          case n: RootNode => true
          case n: ContainerNode =>
            nameMatch(n.name, namePattern) || n.children.values.exists { x => matchPattern(x) }

          case n: Node => nameMatch(n.name, namePattern)
          case _       => false
        }
      }
      val node = element.asInstanceOf[Node]
      val matched = matchPattern(element)

      if (matched) {
        matchedNode = Some(node)
      }
      matched
    }
  }

  def setMatcherString(pattern: String, update: Boolean) = {
    namePattern = pattern

    if (update)
      stringMatcherUpdated()
  }

  protected def stringMatcherUpdated() = {
    treeViewer.getControl().setRedraw(false)
    treeViewer.refresh()
    treeViewer.expandAll()
    selectMatchedNode();
    treeViewer.getControl().setRedraw(true)
  }

  private def selectMatchedNode(): Unit = {
    def findElement(node: Node, items: Array[TreeItem]): Option[TreeItem] = {
      if (items.isEmpty)
        None
      else
        items.find { x => x.getData == node } orElse findElement(node, items.flatMap(_.getItems))
    }

    for {
      node <- matchedNode
      treeItem <- findElement(node, treeViewer.getTree.getItems)
    } {
      treeViewer.getTree.setSelection(treeItem)
    }
  }

  def setInput(input: Any): Unit = {
    treeViewer.setInput(input)
    if (ScalaPlugin().getPreferenceStore().getBoolean(EditorPreferencePage.P_INITIAL_IMPORT_FOLD))
      OutlineHelper.foldImportNodes(treeViewer, input)
  }

  def addDisposeListener(listener: org.eclipse.swt.events.DisposeListener): Unit = {
    getShell().addDisposeListener(listener)
  }

  def addFocusListener(listener: org.eclipse.swt.events.FocusListener): Unit = {
    getShell().addFocusListener(listener)
  }

  def computeSizeHint(): org.eclipse.swt.graphics.Point = getShell().getSize()

  def dispose(): Unit = close()

  def isFocusControl(): Boolean = getShell().getDisplay().getActiveShell() eq getShell()

  def removeDisposeListener(listener: org.eclipse.swt.events.DisposeListener): Unit = getShell().removeDisposeListener(listener)

  def removeFocusListener(listener: org.eclipse.swt.events.FocusListener): Unit = getShell().removeFocusListener(listener)

  def setBackgroundColor(background: org.eclipse.swt.graphics.Color): Unit = applyBackgroundColor(background, getContents())

  def setFocus(): Unit = {
    getShell().forceFocus()
    filterText.setFocus()
  }

  def setForegroundColor(foreground: org.eclipse.swt.graphics.Color): Unit = applyForegroundColor(foreground, getContents())

  def setInformation(info: String): Unit = {}

  def setLocation(location: org.eclipse.swt.graphics.Point): Unit = {
    if (!getPersistLocation() || getDialogSettings() == null)
      getShell().setLocation(location)
  }

  def setSize(width: Int, height: Int): Unit = getShell().setSize(width, height)

  def setSizeConstraints(width: Int, height: Int): Unit = {}

  def setVisible(visible: Boolean): Unit = {
    if (visible) {
      open()
    } else {
      //removeHandlerAndKeyBindingSupport()
      saveDialogBounds(getShell())
      getShell().setVisible(false)
    }
  }

  def hasContents(): Boolean = {
    treeViewer.getInput() ne null
  }

  private def installFilter() = {
    filterText.setText("")

    filterText.addModifyListener { (e: ModifyEvent) =>
      val text = e.widget.asInstanceOf[Text].getText()
      setMatcherString(text, true)
    }
  }

  private def gotoSelectedElement() = {
    val selectedElement = getSelectedElement()
    selectedElement match {
      case n: Node =>
        editor.selectAndReveal(n.start, n.end - n.start)
      case _ =>
    }

    if (selectedElement ne null) {
      dispose()
    }
  }

  def getSelectedElement() = {
    treeViewer.getSelection().asInstanceOf[IStructuredSelection].getFirstElement
  }

  def createFilterText(parent: Composite): Text = {
    filterText = new Text(parent, SWT.NONE)
    Dialog.applyDialogFont(filterText)

    val data = new GridData(GridData.FILL_HORIZONTAL)
    data.horizontalAlignment = GridData.FILL
    data.verticalAlignment = GridData.CENTER
    filterText.setLayoutData(data)

    filterText.addKeyListener(new KeyListener() {
      def keyPressed(e: KeyEvent) = {
        if (e.keyCode == 0x0D || e.keyCode == SWT.KEYPAD_CR) // Enter key
          gotoSelectedElement()
        if (e.keyCode == SWT.ARROW_DOWN)
          treeViewer.getTree().setFocus()
        if (e.keyCode == SWT.ARROW_UP)
          treeViewer.getTree().setFocus()
        if (e.character == 0x1B) // ESC
          dispose()
      }
      def keyReleased(e: KeyEvent) = {
        // do nothing
      }
    })

    filterText
  }

  override def createTitleControl(parent: Composite) = {
    filterText = createFilterText(parent)
    filterText
  }

  def fillViewMenu(viewMenu: IMenuManager) = {
    viewMenu.add(new Separator("Sorters"))
    viewMenu.add(new LexicalSortingAction(treeViewer))
    viewMenu.add(new PublicOnlyAction(contentProvider, treeViewer))
  }

  override def fillDialogMenu(dialogMenu: IMenuManager) = {
    super.fillDialogMenu(dialogMenu)
    fillViewMenu(dialogMenu)
  }

  override def getDialogSettings() = {
    import org.scalaide.core.internal.ScalaPlugin
    val sectionName = getId()
    var settings = ScalaPlugin().getDialogSettings().getSection(sectionName)
    if (settings eq null)
      settings = ScalaPlugin().getDialogSettings().addNewSection(sectionName)

    settings
  }
}
