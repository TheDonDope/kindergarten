package de.clearit.kindergarten.appliance.vendor;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;

import com.jgoodies.application.Application;
import com.jgoodies.application.ResourceMap;
import com.jgoodies.binding.adapter.BasicComponentFactory;
import com.jgoodies.binding.adapter.SingleListSelectionAdapter;
import com.jgoodies.forms.factories.Borders;
import com.jgoodies.forms.factories.CC;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.jsdl.core.pane.form.FormPane;
import com.jgoodies.uif2.AbstractView;
import com.jgoodies.uif2.builder.I15dPanelBuilder2;
import com.jgoodies.uif2.component.StripedTable;
import com.jgoodies.uif2.util.TextComponentUtils;

import de.clearit.kindergarten.domain.VendorBean;
import de.clearit.validation.view.IconFeedbackPanel;

/**
 * The editor view for the vendor.
 */
public final class VendorEditorView extends AbstractView {

  private static final ResourceMap RESOURCES = Application.getResourceMap(VendorEditorView.class);

  private final VendorEditorModel model;

  private JComponent firstNameField;
  private JComponent lastNameField;
  private JComponent phoneNumberField;

  private JTextField vendorNumberField;
  private JTable vendorNumberTable;
  private JButton addVendorNumberButton;
  private JButton removeVendorNumberButton;

  // Instance Creation ******************************************************
  public VendorEditorView(VendorEditorModel model) {
    this.model = model;
  }

  // Initialisation *********************************************************
  private void initComponents() {
    firstNameField = BasicComponentFactory.createTextField(model.getBufferedModel(VendorBean.PROPERTY_FIRST_NAME));
    lastNameField = BasicComponentFactory.createTextField(model.getBufferedModel(VendorBean.PROPERTY_LAST_NAME));
    phoneNumberField = BasicComponentFactory.createTextField(model.getBufferedModel(VendorBean.PROPERTY_PHONE_NUMBER));
    vendorNumberTable = new StripedTable(new VendorNumberTableModel(model.getSelectionInList()));
    vendorNumberTable.setSelectionModel(new SingleListSelectionAdapter(model.getSelectionInList()
        .getSelectionIndexHolder()));
    addVendorNumberButton = new JButton(model.getAction(VendorEditorModel.ACTION_ADD_VENDOR_NUMBER));
    removeVendorNumberButton = new JButton(model.getAction(VendorEditorModel.ACTION_REMOVE_VENDOR_NUMBER));
    vendorNumberField = BasicComponentFactory.createTextField(model.getVendorNumberFieldModel());
  }

  private void initActionHandling() {
    vendorNumberField.addActionListener(actionEvent -> {
      TextComponentUtils.commitImmediately();
      if (model.getVendorNumberFieldModel().getValue() == null) {
        model.performAccept(actionEvent);
        return;
      } else {
        model.addVendorNumber(actionEvent);
        vendorNumberField.requestFocusInWindow();
      }
    });
    addVendorNumberButton.addActionListener(actionEvent -> {
      vendorNumberField.requestFocusInWindow();
    });
  }

  @Override
  protected JComponent buildPanel() {
    initComponents();
    initActionHandling();
    FormPane pane = new FormPane(buildContent(), model);
    pane.setBackground(RESOURCES.getColor("content.background"));
    return pane;
  }

  public JComponent buildContent() {
    FormLayout layout = new FormLayout("150dlu:grow, 16dlu, 70dlu, pref:grow",
        "2*(p, p, $lg),p, p,30dlu, p , pref , pref , 30dlu ,pref, pref:grow, p");
    I15dPanelBuilder2 builder = new I15dPanelBuilder2(layout, RESOURCES);
    builder.setBorder(Borders.DLU14_BORDER);
    builder.addI15dLabel("vendor.firstName", CC.xy(1, 1));
    builder.add(firstNameField, CC.xy(1, 2));
    builder.addI15dLabel("vendor.lastName", CC.xy(1, 4));
    builder.add(lastNameField, CC.xy(1, 5));
    builder.addI15dLabel("vendor.phoneNumber", CC.xy(1, 7));
    builder.add(phoneNumberField, CC.xy(1, 8));
    builder.addI15dLabel("vendor.vendorNumber", CC.xy(1, 10));
    builder.add(vendorNumberField, CC.xy(1, 12));
    builder.add(addVendorNumberButton, CC.xy(3, 12));
    builder.add(new JScrollPane(vendorNumberTable), CC.xywh(1, 14, 1, 3));
    builder.add(removeVendorNumberButton, CC.xy(3, 16));
    return IconFeedbackPanel.getWrappedComponentTree(model.getValidationSupport().resultModel(), builder.getPanel());
  }

}
