package de.clearit.kindergarten.appliance.purchase;

import java.awt.Color;
import java.awt.Font;
import java.text.NumberFormat;
import java.util.Locale;

import javax.swing.JComponent;
import javax.swing.JLabel;

import com.jgoodies.application.Application;
import com.jgoodies.application.ResourceMap;
import com.jgoodies.forms.builder.PanelBuilder;
import com.jgoodies.forms.factories.CC;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.uif2.AbstractView;

import de.clearit.kindergarten.appliance.IntegerToStringConverter;
import de.clearit.kindergarten.application.KindergartenComponentFactory;

class PurchaseEditorSummary extends AbstractView {

  private static final ResourceMap RESOURCES = Application.getResourceMap(PurchaseEditorSummary.class);

  private JComponent itemCountLabel;
  private JComponent itemCountField;
  private JComponent itemSumLabel;
  private JComponent itemSumField;

  private final PurchaseEditorModel model;

  // Instance Creation ******************************************************

  public PurchaseEditorSummary(final PurchaseEditorModel model) {
    this.model = model;
  }

  // Building ***************************************************************

  @Override
  protected JComponent buildPanel() {
    initComponents();
    initFonts();

    FormLayout layout = new FormLayout("left:pref, $lcgap, right:75dlu", "p, $lg, p");
    PanelBuilder builder = new PanelBuilder(layout);
    builder.setBackground(Color.WHITE);
    builder.add(itemCountLabel, CC.xy(1, 1));
    builder.add(itemCountField, CC.xy(3, 1));
    builder.add(itemSumLabel, CC.xy(1, 3));
    builder.add(itemSumField, CC.xy(3, 3));
    return builder.getPanel();
  }

  private void initComponents() {
    itemCountLabel = new JLabel(RESOURCES.getString("purchase.itemCount"));
    itemCountField = KindergartenComponentFactory.createReadOnlyTextField(new IntegerToStringConverter(model
        .getItemCountModel()));
    itemSumLabel = new JLabel(RESOURCES.getString("purchase.itemSum"));
    itemSumField = KindergartenComponentFactory.createReadOnlyFormattedTextField(model.getItemSumModel(), NumberFormat
        .getCurrencyInstance(Locale.GERMANY));
  }

  private void initFonts() {
    Font bold18PtLabelFont = itemCountLabel.getFont().deriveFont(Font.BOLD, 18);
    itemCountLabel.setFont(bold18PtLabelFont);
    itemSumLabel.setFont(bold18PtLabelFont);
    Font regurlar18PtFieldFont = itemCountLabel.getFont().deriveFont(18.0f);
    itemCountField.setFont(regurlar18PtFieldFont);
    itemSumField.setFont(regurlar18PtFieldFont);
  }

}
