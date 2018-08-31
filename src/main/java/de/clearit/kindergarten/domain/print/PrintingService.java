package de.clearit.kindergarten.domain.print;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintServiceLookup;
import javax.print.ServiceUI;
import javax.print.SimpleDoc;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.clearit.kindergarten.domain.VendorBean;
import de.clearit.kindergarten.domain.VendorService;
import de.clearit.kindergarten.domain.export.ExportReceipt;
import de.clearit.kindergarten.domain.export.entity.PayoffDataReceipt;
import de.clearit.kindergarten.domain.export.service.ExportDataService;

public class PrintingService {

  private static final Logger LOGGER = LoggerFactory.getLogger(PrintingService.class);
  private static List<File> allPDFFiles = new ArrayList<>();

  private PrintingService() {
    super();
  }

  public static void printAllExportedFiles() throws IOException {
    List<VendorBean> vendorList = VendorService.getInstance().getAll();
    vendorList.forEach(PrintingService::createFile);
    //Creates one File for printing
    sendToPrinter();
  }

  private static void createFile(VendorBean vendor) {
    PayoffDataReceipt payoffData = ExportDataService.getPayoffDataForVendor(vendor);
    File pdfToPrint = new File(ExportReceipt.getInstance().getDateiname(payoffData).replace(".xlsx", ".pdf"));
    allPDFFiles.add(pdfToPrint);
   }
  
  @SuppressWarnings("deprecation")
  private static void sendToPrinter() throws IOException {
	  final String path = System.getProperty("user.home") + "/Desktop/Basar Abrechnungen/Gesamt Abrechnung.pdf";
	  PDFMergerUtility pdfMerger = new PDFMergerUtility();
	  //If u remove this, u got a Message in the console:
	  //INFO To get higher rendering speed on JDK8 or later,
	  //INFO use the option -Dsun.java2d.cmm=sun.java2d.cmm.kcms.KcmsServiceProvider
	  //INFO or call System.setProperty("sun.java2d.cmm", "sun.java2d.cmm.kcms.KcmsServiceProvider") 
	  System.setProperty("sun.java2d.cmm", "sun.java2d.cmm.kcms.KcmsServiceProvider"); 
	  if(allPDFFiles.size() != 0) {
		  for (File file : allPDFFiles) {
			pdfMerger.addSource(file);
		  }
		  pdfMerger.setDestinationFileName(path);
		  pdfMerger.mergeDocuments();
		  
		  File pdfToPrint = new File(pdfMerger.getDestinationFileName());
		  
		  DocFlavor flavor = DocFlavor.SERVICE_FORMATTED.PAGEABLE;
		  PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
		  javax.print.PrintService[] services = PrintServiceLookup.lookupPrintServices(flavor, attributes);
		  javax.print.PrintService defaultService = PrintServiceLookup.lookupDefaultPrintService();

		  if (defaultService == null) {
			  LOGGER.debug("Error - No default print service found");
		  }else {
			  int reply = JOptionPane.showConfirmDialog(null, "Sie sind dabei ALLE Abrechnungen zu drucken. M�chten Sie fortfahren?" , "Alle Abrechnunge Drucken",JOptionPane.YES_NO_OPTION);
			  if(reply == JOptionPane.YES_OPTION) {
				  javax.print.PrintService service = ServiceUI.printDialog(null, 200, 200, services, defaultService, flavor, attributes);
				  if(service != null) {
			  		DocPrintJob docPrintJob = service.createPrintJob();
			  		try {
			          PDDocument pdDocument = PDDocument.load(pdfToPrint);	
			          // Drucken als Pageable
			          PDFPageable pdfPageable = new PDFPageable(pdDocument);
			          SimpleDoc docToPrint = new SimpleDoc(pdfPageable, flavor, null);
			          docPrintJob.print(docToPrint, null);
			        }catch(PrintException e) {
			        	LOGGER.debug("Error - Unable to print");
			        	LOGGER.error(e.getMessage());
			        }catch (IOException e) {
			          LOGGER.error(e.getMessage());
			        }
				  }
			  }else {
				  JOptionPane.showMessageDialog(new JFrame(), "Gesamtabrechnung wurde erstellt!\n" + path);
			  }
		  }
	  }else {
		  JOptionPane.showMessageDialog(new JFrame(), "Keine Verk�ufer erfasst Drucken nicht m�glich!", "Fehler", JOptionPane.ERROR_MESSAGE);
	  }
  }
}
