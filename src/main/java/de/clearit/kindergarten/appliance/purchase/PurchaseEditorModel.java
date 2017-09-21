package de.clearit.kindergarten.appliance.purchase;

import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.EventObject;

import com.jgoodies.application.Action;
import com.jgoodies.application.Application;
import com.jgoodies.application.ResourceMap;
import com.jgoodies.binding.list.SelectionInList;
import com.jgoodies.desktop.CommitCallback;
import com.jgoodies.desktop.DesktopManager;
import com.jgoodies.jsdl.core.CloseRequestHandler;
import com.jgoodies.jsdl.core.CommandValue;
import com.jgoodies.jsdl.core.MessageType;
import com.jgoodies.jsdl.core.PreferredWidth;
import com.jgoodies.jsdl.core.pane.TaskPane;
import com.jgoodies.jsdl.core.pane.form.FormPaneModel;
import com.jgoodies.jsdl.core.util.JSDLUtils;
import com.jgoodies.uif2.application.UIFPresentationModel;
import com.jgoodies.uif2.util.TextComponentUtils;

import de.clearit.kindergarten.application.Dialogs;
import de.clearit.kindergarten.domain.PurchaseBean;
import de.clearit.kindergarten.domain.PurchaseService;
import de.clearit.kindergarten.domain.VendorBean;
import de.clearit.kindergarten.domain.VendorService;

public class PurchaseEditorModel extends UIFPresentationModel<PurchaseBean> implements FormPaneModel {

  private static final long serialVersionUID = 1L;
  private static final ResourceMap RESOURCES = Application.getResourceMap(PurchaseEditorModel.class);
  private static final PurchaseService SERVICE = PurchaseService.getInstance();

  // Constants **************************************************************

  public static final String ACTION_ADD_LINE_ITEM = "addLineItem";
  public static final String ACTION_REMOVE_LINE_ITEM = "removeLineItem";

  // Instance Fields ********************************************************

  private final SelectionInList<VendorBean> vendorList;
  private final CommitCallback<CommandValue> commitCallback;
  private final long creationTime;
  private SelectionInList<PurchaseBean> selectionInList;

  // Instance Creation ******************************************************

  PurchaseEditorModel(final PurchaseBean purchase, final CommitCallback<CommandValue> callback) {
    super(purchase);
    vendorList = new SelectionInList<>();
    vendorList.getList().addAll(VendorService.getInstance().getAll());
    this.commitCallback = callback;
    this.creationTime = System.currentTimeMillis();
    initModels();
    initPresentationLogic();
  }

  // Initialization *********************************************************

  protected void initModels() {
    selectionInList = new SelectionInList<>();
    if (selectionInList.getList().size() > 0) {
      selectionInList.setSelectionIndex(0);
    }
    handleSelectionChange(selectionInList.hasSelection());
  }

  protected void initPresentationLogic() {
    getSelectionInList().addPropertyChangeListener(SelectionInList.PROPERTYNAME_SELECTION, new SelectionChangeHandler(
        this));
  }

  // Models *****************************************************************

  public SelectionInList<PurchaseBean> getSelectionInList() {
    return selectionInList;
  }

  public PurchaseBean getSelection() {
    return getSelectionInList().getSelection();
  }

  public SelectionInList<VendorBean> getVendorList() {
    return vendorList;
  }

  // Event Handling *********************************************************

  protected void handleSelectionChange(final boolean hasSelection) {
    handleSelectionChangeEditDelete(hasSelection);
  }

  protected void handleSelectionChangeEditDelete(final boolean hasSelection) {
    setActionEnabled(ACTION_REMOVE_LINE_ITEM, hasSelection);
  }

  // Actions ****************************************************************

  @Action
  public void addLineItem(final ActionEvent e) {
    TextComponentUtils.commitImmediately();
    triggerCommit();
    getSelectionInList().getList().add(getBean());
    setBean(new PurchaseBean());
  }

  @Action(enabled = false)
  public void removeLineItem(final ActionEvent e) {
    final PurchaseBean purchase = getSelection();
    final String mainInstruction = RESOURCES.getString("deleteItem.mainInstruction", "Artikel-Nr: " + purchase
        .getItemNumber());
    final TaskPane pane = new TaskPane(MessageType.QUESTION, mainInstruction, CommandValue.YES, CommandValue.NO);
    pane.setPreferredWidth(PreferredWidth.MEDIUM);
    pane.showDialog(e, RESOURCES.getString("deleteItem.title"));
    if (pane.getCommitValue() == CommandValue.YES) {
      getSelectionInList().getList().remove(purchase);
    }
  }

  // FormPaneModel Implementation *******************************************

  @Override
  public void performAccept(final EventObject e) {
    TextComponentUtils.commitImmediately();
    triggerCommit();
    getSelectionInList().getList().forEach(purchaseBean -> SERVICE.create(purchaseBean));
    commitCallback.committed(CommandValue.OK);
    JSDLUtils.closePaneFor(e);
  }

  @Override
  public String getAcceptText() {
    return RESOURCES.getString("editorModel.acceptText");
  }

  @Override
  public void performApply(final EventObject e) {
    // Do nothing.
  }

  @Override
  public void performCancel(final EventObject e) {
    paneClosing(e, CloseRequestHandler.NO_OPERATION);
  }

  @Override
  public void paneClosing(final EventObject e, final Runnable operation) {
    final Runnable cancelOp = new WrappedOperation(commitCallback, CommandValue.CANCEL, operation);
    TextComponentUtils.commitImmediately();
    if (!isChanged() && !isBuffering()) { // Test for searching
      System.out.println("Nichts geaendert");
      cancelOp.run();
      return;
    }
    final String objectName = "Artikel-Nr: " + getBean().getItemNumber();
    final Object commitValue = Dialogs.showUnsavedChangesDialog(e, objectName);
    if (commitValue == CommandValue.CANCEL) {
      return;
    }
    if (commitValue == CommandValue.DONT_SAVE) {
      cancelOp.run();
      return;
    }
    final Runnable acceptOp = new WrappedOperation(commitCallback, CommandValue.OK, operation);
    acceptOp.run();
    // Eigentlich: executeOnSearchEnd(new ValidateAndSaveTask(acceptOp));
  }

  @Override
  public boolean isApplyVisible() {
    return false;
  }

  @Override
  public boolean isApplyEnabled() {
    return false;
  }

  // Event Handlers *********************************************************

  private static final class SelectionChangeHandler implements PropertyChangeListener {

    private final PurchaseEditorModel model;

    SelectionChangeHandler(final PurchaseEditorModel model) {
      this.model = model;
    }

    @Override
    public void propertyChange(final PropertyChangeEvent evt) {
      model.handleSelectionChange(model.getSelectionInList().hasSelection());
    }
  }

  // Helper Code ************************************************************

  private final class WrappedOperation implements Runnable {

    private final CommitCallback<CommandValue> commitCallback;
    private final CommandValue commitValue;
    private final Runnable postOperation;

    WrappedOperation(final CommitCallback<CommandValue> commitCallback, final CommandValue commitValue,
        final Runnable postOperation) {
      this.commitCallback = commitCallback;
      this.commitValue = commitValue;
      this.postOperation = postOperation;
    }

    @Override
    public void run() {
      if (commitValue == CommandValue.OK) {
        triggerCommit();
      } else {
        triggerFlush();
      }
      commitCallback.committed(commitValue);
      DesktopManager.closeActiveFrame();
      postOperation.run();
    }
  }

}
