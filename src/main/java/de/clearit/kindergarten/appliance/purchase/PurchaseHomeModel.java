package de.clearit.kindergarten.appliance.purchase;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EventObject;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.JFileChooser;
import javax.swing.ListModel;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonWriter;
import com.jgoodies.application.Action;
import com.jgoodies.application.Application;
import com.jgoodies.application.BlockingScope;
import com.jgoodies.application.ResourceMap;
import com.jgoodies.application.Task;
import com.jgoodies.binding.list.SelectionInList;
import com.jgoodies.binding.value.ValueHolder;
import com.jgoodies.binding.value.ValueModel;
import com.jgoodies.jsdl.core.CommandValue;
import com.jgoodies.jsdl.core.MessageType;
import com.jgoodies.jsdl.core.PreferredWidth;
import com.jgoodies.jsdl.core.pane.TaskPane;

import de.clearit.kindergarten.appliance.AbstractHomeModel;
import de.clearit.kindergarten.appliance.PostChangeHandler;
import de.clearit.kindergarten.domain.PurchaseBean;
import de.clearit.kindergarten.domain.PurchaseService;
import de.clearit.kindergarten.domain.VendorBean;
import de.clearit.kindergarten.domain.VendorNumberBean;
import de.clearit.kindergarten.domain.VendorNumberService;
import de.clearit.kindergarten.domain.VendorService;
import de.clearit.kindergarten.domain.entity.PurchaseVendorEntity;
import io.reactivex.Observable;

/**
 * The home model for the purchase.
 */
public class PurchaseHomeModel extends AbstractHomeModel<PurchaseBean> implements PostChangeHandler  {

  private static final long serialVersionUID = 1L;

  public static final String ACTION_IMPORT_PURCHASES = "importPurchases";
  public static final String ACTION_EXPORT_PURCHASES = "exportPurchases";
  private static final Logger LOGGER = LoggerFactory.getLogger(PurchaseHomeModel.class);
  private static final ResourceMap RESOURCES = Application.getResourceMap(PurchaseHomeModel.class);
  private static final PurchaseService SERVICE = PurchaseService.getInstance();
  private static final VendorService VENDOR_SERVICE = VendorService.getInstance();
  private static PurchaseHomeModel instance;
  private final transient SelectionInList<VendorBean> vendorList;
  private final transient SelectionInList<String> sortList;
  private final transient ValueModel itemCountModel = new ValueHolder(0);
  private final transient ValueModel itemSumModel = new ValueHolder(0.0);
  private final transient ValueModel kindergartenProfitModel = new ValueHolder(0.0);
  private final transient ValueModel vendorPayoutModel = new ValueHolder(0.0);
  private File file;
  
  // Instance Creation ******************************************************

  private PurchaseHomeModel() {
    super();
    vendorList = new SelectionInList<>();
    sortList = new SelectionInList<>();
    vendorList.getList().add(alleVerkaeufer());
    vendorList.getList().addAll(VENDOR_SERVICE.getAll());
    vendorList.setSelectionIndex(0);
    refreshSummary();
    vendorList.addValueChangeListener(klickEvent -> filterPurchases());
    sortList.addValueChangeListener(klickEvent -> sortPurchases());
  }

  public static PurchaseHomeModel getInstance() {
    if (instance == null) {
      instance = new PurchaseHomeModel();
    }
    return instance;
  }

  public SelectionInList<VendorBean> getVendorList() {
    return vendorList;
  }
  
  public SelectionInList<String> getSortList()
  {
	  sortList.getList().add(0, "Chronologisch");
	  sortList.getList().add(1, "Nach Verkäufer und Verkäufernummer");
	  sortList.setSelectionIndex(0);
	  return sortList;
  }

  public ValueModel getItemCountModel() {
    return itemCountModel;
  }

  public ValueModel getItemSumModel() {
    return itemSumModel;
  }

  public ValueModel getKindergartenProfitModel() {
    return kindergartenProfitModel;
  }

  public ValueModel getVendorPayoutModel() {
    return vendorPayoutModel;
  }

  // Initialisation *********************************************************

  @Override
  protected ListModel<PurchaseBean> getListModel() {
    return SERVICE.getListModel();
  }

  // Presentation Logic *****************************************************

  @Override
  protected void handleSelectionChange(final boolean hasSelection) {
    handleSelectionChangeEditDelete(hasSelection);
  }

  // Actions ****************************************************************

  @Override
  protected String[] contextActionNames() {
    return new String[] {};
  }

  @Action
  public void newItem(final ActionEvent e) {
    final String title = RESOURCES.getString("newPurchase.title");
    //editItem(e, title, new PurchaseBean(), true);
    editItem(e, title, new PurchaseVendorEntity(), true);
  }

  @Action(enabled = false)
  public void editItem(final ActionEvent e) {
    final String title = RESOURCES.getString("editPurchase.title");
    editItem(e, title, getSelection(), false);
  }

  private void editItem(final EventObject e, final String title, final PurchaseBean purchase, final boolean newItem) {
    final PurchaseEditorModel model = new PurchaseEditorModel(purchase, value -> {
      if (value == CommandValue.OK) {
        getVendorList().setSelectionIndex(0);
        refreshSummary();
      }
    });
    PurchaseAppliance.getInstance().openPurchaseEditor(title, model);
  }

  @Action(enabled = false)
  public void deleteItem(final ActionEvent e) {
    final PurchaseBean purchase = getSelection();
    final String mainInstruction = RESOURCES.getString("deleteItem.mainInstruction", "Artikel-Nr: " + purchase
        .getItemNumber());
    final TaskPane pane = new TaskPane(MessageType.QUESTION, mainInstruction, CommandValue.YES, CommandValue.NO);
    pane.setPreferredWidth(PreferredWidth.MEDIUM);
    pane.showDialog(e, RESOURCES.getString("deleteItem.title"));
    if (pane.getCommitValue() == CommandValue.YES) {
      SERVICE.delete(purchase);
      filterPurchases();
    }
  }

  @Action
  public Task<List<PurchaseBean>, Void> importPurchases(final ActionEvent e) {
    LOGGER.debug("Importing purchase\u2026");
    return new ImportPurchasesTask();
  }

  @Action
  public Task<Void, Void> exportPurchases(final ActionEvent e) {
    LOGGER.debug("Exporting purchase\u2026");
    return new ExportPurchasesTask();
  }

  @Override
  public void onPostCreate() {
    refreshVendorList();
    refreshSummary();
  }

  @Override
  public void onPostUpdate() {
    refreshVendorList();
    refreshSummary();
  }

  @Override
  public void onPostDelete() {
    refreshVendorList();
    refreshSummary();
  }

  private void refreshSummary() {
    List<PurchaseBean> purchaseBeanList = getSelectionInList().getList();
	itemCountModel.setValue(SERVICE.getItemCountByPurchases(purchaseBeanList));
    itemSumModel.setValue(SERVICE.getItemSumByPurchases(purchaseBeanList));
    kindergartenProfitModel.setValue(SERVICE.getKindergartenProfitByPurchases(purchaseBeanList));
    vendorPayoutModel.setValue(SERVICE.getVendorPayoutByPurchases(purchaseBeanList));
  }

  private VendorBean alleVerkaeufer() {
    VendorBean alleVerkaeufer = new VendorBean();
    alleVerkaeufer.setLastName("Alle");
    return alleVerkaeufer;
  }
  
  private void filterPurchases() {
	  
	    VendorBean selectedVendor = getVendorList().getSelection();
	    
	    if(selectedVendor != null){
	    List<PurchaseVendorEntity> filteredOrAllPurchases = new ArrayList<>();

	    if (alleVerkaeufer().equals(selectedVendor)) {
	      filteredOrAllPurchases.addAll(transformToPurchaseVendorEntity(SERVICE.getAll()));
	    } else {
	      // 1. Hole alle Purchases vom Service
	      List<PurchaseBean> allPurchases = SERVICE.getAll();

	      // 2. Filtere dabei nach Purchases, bei welchem die VendorNumber == eine der
	      // VendorNumbers des selektierten Vendors aus der ComboBox ist

	      List<PurchaseBean> filteredPurchases = allPurchases.stream().filter(bean -> selectedVendor.getVendorNumbers()
	          .stream().map(VendorNumberBean::getVendorNumber).collect(Collectors.toList()).contains(bean
	              .getVendorNumber())).collect(Collectors.toList());

	      // 3. Fuege die gefilterten Suchergebnisse der Ergebnisliste hinzu
	      filteredOrAllPurchases.addAll(transformToPurchaseVendorEntity(filteredPurchases));
	    }
	    // 4. Leere die Tabelle der Purchases
	    getSelectionInList().getList().clear();
	    // 5. Fuege der Tabelle die Ergebnisliste mit den gefilterten Surchergebnissen
	    // hinzu
	    getSelectionInList().getList().addAll(filteredOrAllPurchases);
	    // 6. Aktualisiere die Daten in der Gesamtberechnung rechts oben
	    refreshSummary();
	  }
  }

	
	 private void sortPurchases()
	  {
		  String selectedSortMode = sortList.getSelection();
		  List<PurchaseBean> vendorsToBeSorted = new ArrayList<>();

		  // Chronologische Sortierung
		  if (selectedSortMode.equals(sortList.getElementAt(0)))
		  {
			  vendorsToBeSorted.addAll(SERVICE.getAll());
		  }

		  //Sortierung nach Name und VendorNumber
		  else
		  {
			  List <PurchaseVendorEntity> entityList = new ArrayList<>();

			  Comparator<PurchaseVendorEntity> sortByNameAndVendorNumberAndItemNumber = Comparator.comparing(PurchaseVendorEntity::getVendorFullName)
	              .thenComparing(PurchaseVendorEntity::getVendorNumber).thenComparing(PurchaseVendorEntity::getItemNumber);

			  List<PurchaseBean> allBeans = SERVICE.getAll();

			  entityList = transformToPurchaseVendorEntity(allBeans);
			  Collections.sort(entityList, sortByNameAndVendorNumberAndItemNumber);

			  vendorsToBeSorted.addAll(transformToPurchaseBean(entityList));
		  }

	    getSelectionInList().getList().clear();
	    getSelectionInList().getList().addAll(vendorsToBeSorted);
	  }

		private List<PurchaseVendorEntity> transformToPurchaseVendorEntity(List<PurchaseBean> purchaseBeans)
		{
			List<PurchaseVendorEntity> entityList = new ArrayList<>();
			for(PurchaseBean purchase : purchaseBeans) {
				  PurchaseVendorEntity entity = new PurchaseVendorEntity();
				  entity.setId(purchase.getId());
				  entity.setItemNumber(purchase.getItemNumber());
				  entity.setItemPrice(purchase.getItemPrice());
				  entity.setVendorNumber(purchase.getVendorNumber());
				  VendorBean vendorBean = VendorService.getInstance().findByVendorNumber(purchase.getVendorNumber());
				  if (vendorBean != null) {
	          entity.setVendorFullName(vendorBean.getLastName() + ", " + vendorBean.getFirstName());
	        }
				  entityList.add(entity);
			  }
			return entityList;
		}

		private List<PurchaseBean> transformToPurchaseBean(List<PurchaseVendorEntity> entityList) {
	    List<PurchaseBean> purchaseBeanList = new ArrayList<>();	    
	    for(PurchaseVendorEntity entity: entityList) {
	      //Downcasting
	      PurchaseBean purchaseBean = new PurchaseBean();
	      purchaseBean.setId(entity.getId());
	      purchaseBean.setItemNumber(entity.getItemNumber());
	      purchaseBean.setItemPrice(entity.getItemPrice());
	      purchaseBean.setVendorNumber(entity.getVendorNumber());
	      purchaseBeanList.add(purchaseBean);
	    }

	    return purchaseBeanList;
	  }
		
  private void refreshVendorList() {
    vendorList.getList().clear();
    vendorList.getList().add(alleVerkaeufer());
    vendorList.getList().addAll(VENDOR_SERVICE.getAll());
    vendorList.setSelectionIndex(0);
  }

  private final class ImportPurchasesTask extends Task<List<PurchaseBean>, Void> {

    private final TaskPane progressPane;
    private final File importFile;

    ImportPurchasesTask() {
      super(BlockingScope.APPLICATION);
      importFile = getImportPath();
      progressPane = new TaskPane(MessageType.INFORMATION, "Importiere", CommandValue.OK);
      progressPane.setPreferredWidth(PreferredWidth.MEDIUM);
      progressPane.setProgressIndeterminate(true);
      progressPane.setProgressVisible(true);
      progressPane.setVisible(true);
    }
    
    @Override
    protected List<PurchaseBean> doInBackground() throws FileNotFoundException {
    	return new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().fromJson(new FileReader(importFile),
          new TypeToken<List<PurchaseBean>>() {
          }.getType());
    }

    @Override
    protected void succeeded(List<PurchaseBean> result) {
      super.succeeded(result);
      
      List<PurchaseBean> allBeans = SERVICE.getAll();
      List<PurchaseBean> beansToRemove = new ArrayList<>();
      
      for(int resultCount = 0; resultCount < result.size(); resultCount++) {
    	  PurchaseBean resultBean = result.get(resultCount);
    	  for(int allBeansCount = 0; allBeansCount<allBeans.size(); allBeansCount++) {
    		  PurchaseBean existingBean = allBeans.get(allBeansCount);
    		  if(existingBean.getItemNumber().equals(resultBean.getItemNumber()) && existingBean.getVendorNumber().equals(resultBean.getVendorNumber())) {
    			  beansToRemove.add(resultBean);
    			  writeLog(existingBean, resultBean);
    		  }
    	  }
      }
      //TODO Doppelanlegung nicht möglich beim Import!	
	  for(PurchaseBean bean : beansToRemove) {
	   	result.remove(bean);
	  }
      LOGGER.debug("# Purchase elements to create: {}", result.size());
      Observable<PurchaseBean> observablePurchases = Observable.fromIterable(result);
      long beginNanos = System.nanoTime();
      observablePurchases.subscribe(purchaseBean -> SERVICE.toEntity(purchaseBean).saveIt(), Observable::error, () -> {
        SERVICE.flush();
        LOGGER.debug("Finished Import after {} ms", (System.nanoTime() - beginNanos) / 1_000_000);
        progressPane.setVisible(false);
        refreshSummary();
        String mainInstruction = RESOURCES.getString("importPurchases.message.text", result.size());
        TaskPane pane = new TaskPane(MessageType.INFORMATION, mainInstruction, CommandValue.OK);
        pane.setPreferredWidth(PreferredWidth.MEDIUM);
        pane.showDialog(getEventObject(), RESOURCES.getString("importPurchases.message.title"));
      });
    }
    
    //TODO write in Log
    //TODO NEED FIX NOT WORKING APP IS CRASHING WHEN IMPORTING!
    private void writeLog(PurchaseBean existingBean, PurchaseBean importetBean) {
		try {
			if(file == null) {
				file = new File("testLog.txt");
			}
			FileWriter filewriter = new FileWriter(file);		
	    	VendorService vService = VendorService.getInstance();
	    	VendorNumberService vendorNumberService = VendorNumberService.getInstance();
	    	
	    	 
	    	
	    	VendorNumberBean vnb = vendorNumberService.findByVendorNumber(existingBean.getVendorNumber());
	    	VendorBean vendor = vService.getById(vnb.getId());
	    	//Artikel hat VendorNumber
	    	//VendorNumber hat VendorID
	    	//VendorID hat VendorName
	    	
	    	String vendorName = vendor.getFirstName() + " " + vendor.getLastName();
	    	String logMessageExist = vendorName + " " + existingBean.getVendorNumber().toString() + " " +  existingBean.getItemNumber().toString() + " " + existingBean.getItemPrice().toString();
			
	    	filewriter.write(logMessageExist);
	    	filewriter.flush();
	    	filewriter.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.out.println("IOEXCEPTION = " + e);
		}
    }
    
    private File getImportPath() {
      final JFileChooser fileChooser = new JFileChooser(System.getProperty("user.home"));
      fileChooser.addChoosableFileFilter(new FileNameExtensionFilter("JSON", "json"));
      fileChooser.setAcceptAllFileFilterUsed(false);
      fileChooser.setDialogType(JFileChooser.OPEN_DIALOG);
      fileChooser.setDialogTitle("Datei \u00f6ffnen...");
      fileChooser.setVisible(true);
      fileChooser.showOpenDialog(null);
      fileChooser.setVisible(false);

      return fileChooser.getSelectedFile();
    }

  }

  private final class ExportPurchasesTask extends Task<Void, Void> {

    private final TaskPane progressPane;
    private final List<PurchaseBean> purchaseList;
    private final String exportPath;

    ExportPurchasesTask() {
      super(BlockingScope.APPLICATION);
      exportPath = getExportPath() + ".json";
      progressPane = new TaskPane(MessageType.INFORMATION, "Exportiere", CommandValue.OK);
      progressPane.setPreferredWidth(PreferredWidth.MEDIUM);
      progressPane.setProgressIndeterminate(true);
      progressPane.setProgressVisible(true);
      progressPane.setVisible(true);
      purchaseList = SERVICE.getAll();
    }

    @Override
    protected Void doInBackground() throws IOException {
      long beginNanos = System.nanoTime();
      String listPurchasesAsJSONString = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().toJson(
          purchaseList, new TypeToken<List<PurchaseBean>>() {
          }.getType());
      try (JsonWriter jsonWriter = new JsonWriter(new FileWriter(new File(exportPath)))) {
        jsonWriter.jsonValue(listPurchasesAsJSONString);
        jsonWriter.flush();
      }
      LOGGER.debug("Finished Export after {} ms", (System.nanoTime() - beginNanos) / 1_000_000);
      return null;
    }

    @Override
    protected void succeeded(Void result) {
      super.succeeded(result);
      progressPane.setVisible(false);
      String mainInstruction = RESOURCES.getString("exportPurchases.message.text", purchaseList.size(), exportPath);
      TaskPane pane = new TaskPane(MessageType.INFORMATION, mainInstruction, CommandValue.OK);
      pane.setPreferredWidth(PreferredWidth.MEDIUM);
      pane.showDialog(getEventObject(), RESOURCES.getString("exportPurchases.message.title"));
    }

    private String getExportPath() {
      final JFileChooser fileChooser = new JFileChooser(System.getProperty("user.home"));
      fileChooser.setDialogType(JFileChooser.SAVE_DIALOG);
      fileChooser.setDialogTitle("Speichern unter...");
      fileChooser.addChoosableFileFilter(new FileNameExtensionFilter("JSON", "json"));
      fileChooser.setAcceptAllFileFilterUsed(false);
      fileChooser.setVisible(true);
      fileChooser.showSaveDialog(null);
      fileChooser.setVisible(false);

      return fileChooser.getSelectedFile().toString();
    }

  }

}