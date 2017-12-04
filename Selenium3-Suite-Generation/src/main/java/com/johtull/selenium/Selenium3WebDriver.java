package com.johtull.selenium;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.firefox.FirefoxBinary;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.beust.jcommander.JCommander;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.johtull.selenium.SeleniumSettings;
import com.johtull.selenium.xml.MappingRoot;
import com.johtull.selenium.xml.MappingRoot.MappingList;
import com.johtull.selenium.xml.MappingRoot.MappingList.CatentryMapping;


/**
 * TODO: REFACTOR THIS ENTIRE THING
 *
 */
public class Selenium3WebDriver {
	private static final String PRICE_3 = "3.00";
	private static final String PRICE_50 = "50.00";
	private static final int DEFAULT_WAIT_TIME = 15;
	private static final int MAX_OFFENSES = 5;
	private static final int CART_CAP = 95;	//max 100 items in the cart, allow 5 spaces for buffer
	private static final String HTTP_PROTO = "http://";
	
	private static int totalErrors = 0;
	private static int totalProducts = 0;
	private static String resultLog;
	private static int maxProducts;
	
	private static WebDriver webDriver;
	
	private static int debugMismatch = 0;
	
	
	/**
	 * Grab args, run test
	 * @param args
	 */
	public static void main(String[] args) {
		
		// check arguments
		if(args.length == 0) {
			System.out.println("No arguments found");
			return;
		}
		
		// parse args with JCommander
		
		SeleniumSettings settings = new SeleniumSettings();
		JCommander.newBuilder()
			.addObject(settings)
			.build()
			.parse(args);
		//new JCommander(settings, args);
		if(settings.myFileName.isEmpty() || settings.browser.isEmpty() || settings.siteUrl.isEmpty()) {
			System.out.println("Missing arguments");
			return;
		}
		
		// set debug level
		if(settings.debugLevel > 0)
			debugMismatch = settings.debugLevel;
		
		// grab webdriver executable location
		try {
			String currentDir = System.getProperty("user.dir");
			String driverLocation = String.format("%s\\%sdriver.exe", currentDir, settings.browser);
			System.setProperty("webdriver." + settings.browser + ".driver", driverLocation);
		}catch(Exception e) {
			System.out.println("Cannot find " + settings.browser + "driver.exe");
			return;
		}
		
		// if using chrome, default binary to null
		if("chrome".equalsIgnoreCase(settings.browser)) {
			settings.binary = "";
		}
		
		// default to www - needed for intl sites
		String folderName = settings.env;
		if(folderName.isEmpty()) {
			folderName = "www";
		}
		
		resultLog = String.format("%s/%s", folderName, settings.logFile);
		// create result folder
		File myFolder = new File(settings.env);
		if(!myFolder.isDirectory()) {
			myFolder.mkdirs();
		}
		
		// set site url
		String siteUrl = "";
		if(settings.env.isEmpty()) {
			siteUrl = String.format("%s%s/", HTTP_PROTO, settings.siteUrl);
		}else if(settings.env.equalsIgnoreCase("vm")) {
			siteUrl = settings.siteUrl;
		}else {
			siteUrl = String.format("%s%s.%s/", HTTP_PROTO, settings.env, settings.siteUrl);
		}
		
		long starttime = System.currentTimeMillis();
		
		// init error message list
		List<String> errorList = new ArrayList<String>();
		// save timestamp to file
		errorList.add(String.format("Starting test for %s at %s", siteUrl, LocalDateTime.now().toString()));
		saveLog(errorList, false);
		// clear file
		errorList.clear();
		
		try {
			//unmarshall data, choose correct test type
			MappingList myProducts = unmarshallProductData(settings.myFileName);
			
			// set number of products for progress bar
			maxProducts = myProducts.getCatentryMapping().size();
			
			if(settings.isDoorOrder) {
				doorTest(myProducts, settings.binary, siteUrl);
			}else {
				productTest(myProducts, settings.binary, siteUrl);
			}
		} catch (JAXBException e) {
			errorList.add("ERROR: Invalid product list OR product list contains invalid characters " + e.getMessage());
		}
		
		
		long stoptime = System.currentTimeMillis();
		
		errorList.add("-------------------------------------------------");
		errorList.add(String.format("Results for %s", siteUrl));
		errorList.add(String.format("Tested %d products", totalProducts));
		errorList.add(String.format("Runtime: %d seconds", ((stoptime - starttime) / 1000)));
		errorList.add(String.format("Total errors: %d", totalErrors));
		
		saveLog(errorList, true);
	}
	
	/**
	 * Extract product data from file
	 * @param productDataFile
	 * @return MappingList
	 * @throws JAXBException
	 */
	private static MappingList unmarshallProductData(String productDataFile) throws JAXBException {
		//unmarshall the object from xml, put into MappingList for use
		Object myUnmarshalledObject = null;
		JAXBContext myJAXBContext = JAXBContext.newInstance(MappingRoot.class);
		Unmarshaller myUnmarshaller = myJAXBContext.createUnmarshaller();
		myUnmarshalledObject = myUnmarshaller.unmarshal(new File(productDataFile));
		
		MappingList myProducts = ((MappingRoot)myUnmarshalledObject).getMappingList();
		
		return myProducts;
	}
	
	/**
	 * TODO: Replace with FirefoxDriver(FirefoxOptions, FirefoxProfile) or equiv.
	 * 
	 * Used to re-initialize the WebDriver for Firefox, effectively clearing the cache
	 * (fast way to empty the shopping cart)
	 * @param ffBinaryPath
	 * @return new FirefoxDriver
	 */
	public static FirefoxDriver newFFDriver(String ffBinaryPath) {
		File pathToBinary = new File(ffBinaryPath);
		FirefoxBinary ffBinary = new FirefoxBinary(pathToBinary);
		//FirefoxProfile firefoxProfile = new FirefoxProfile();
		//firefoxProfile.setPreference("xpinstall.signatures.required", false);
		//firefoxProfile.setPreference("plugin.disable_full_page_plugin_for_types", "application/pdf");
		
		FirefoxOptions options = new FirefoxOptions();
		options.setBinary(ffBinary);
		return (new FirefoxDriver(options));
	}
	
	/**
	 * Used to re-initialize the WebDriver for chrome
	 * @param binaryPath
	 * @return new ChromeDriver object
	 */
	public static WebDriver newChromeDriver(String binaryPath) {
		return new ChromeDriver();
	}
	
	/**
	 * Specific case to wait for an element to be removed from the DOM
	 * @param myDriver
	 * @param myBy
	 * @param seconds
	 */
	synchronized public static void waitForElementDeath(final WebDriver myDriver, final By myBy, int seconds) {
		(new WebDriverWait(myDriver, seconds)).until(new ExpectedCondition<Boolean>() {
			//@Override
			public Boolean apply(WebDriver driver) {
				return (myDriver.findElements(myBy).size() == 0);
			}
		});
	}
	
	/**
	 * Perform events to add finish option to cart
	 * @param myDriver
	 * @param addToCartBtn
	 * @param currentItem
	 * @param price
	 * @throws Exception - price mismatch
	 * @throws Exception - finish image mismatch
	 * 
	 * TODO: rewrite to scrape for field1 + field2, ensure button visibility is correct
	 */
	public static void addToCart(WebElement addToCartBtn, String currentItem, String price, String imgSrc) throws Exception {
		if(addToCartBtn.isDisplayed()) {
			addToCartBtn.click();
			// breaks if /full cart OR RANDOMLY (FF only)
			WebDriverWait wait = new WebDriverWait(webDriver, DEFAULT_WAIT_TIME);
			WebElement closeBtn = null;
			try {
				//closeBtn = (new WebDriverWait(webDriver, DEFAULT_WAIT_TIME)).until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("a.fancybox-item.fancybox-close")));
				closeBtn = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("a.fancybox-close")));
			}catch(Exception e) {
				throw new Exception(String.format("BUTTON NOT FOUND on: %s\n", currentItem));
			}
			
			String priceVerify = webDriver.findElement(By.id("sample_price_per_item")).getText();
			String imgVerify = webDriver.findElement(By.cssSelector("#sample_thumbnail_ordered .overrideImageWidth")).getAttribute("src");
			
			closeBtn.click();
			waitForElementDeath(webDriver, By.cssSelector("div.fancybox-overlay"), DEFAULT_WAIT_TIME);
			
			if(!priceVerify.contains(price)) {
				throw new Exception(String.format("Bad SKU Price: %s\n", currentItem));
			}
			
			// if enabled, debug mismatching images
			if(imgSrc != null && !imgSrc.isEmpty()) {
				if(!imgVerify.contains(imgSrc)) {
					throw new Exception(String.format("Finish image mismatch - Finish: %s - Modal: %s\n", imgVerify, imgSrc));
				}
			}
		}
	}
	
	/**
	 * Overloaded
	 */
	public static void addToCart(WebElement addToCartBtn, String currentItem, String price) throws Exception {		
		addToCart(addToCartBtn, currentItem, price, null);
	}
	
	/**
	 * Check the quantity of the cart by parsing the #cartQuantity JSON
	 * @param myDriver
	 * @return
	 */
	public static int getCartQuantity(WebDriver myDriver) {
		String cartData = myDriver.findElement(By.id("cartQuantity")).getAttribute("data-recent-cart");
		cartData = cartData.substring(1, cartData.length() - 1);
		if(cartData.isEmpty()) {
			return 0;
		}
		JsonElement jsonElement = (new JsonParser()).parse(cartData);
		return jsonElement.getAsJsonObject().get("orderQty").getAsInt();
	}
	
	/**
	 * Replace ASCII and UTF-8 characters with '*', replace consecutive spaces with a single space
	 * @param str
	 * @return formatted string
	 */
	private static String normalizeName(String str) {
		if(str != null && !str.isEmpty()) {
			// TM	�	&#8482; or &#153;
			// R	�	&#174;
			str = str.replaceAll("(�|�|&#8482;|&#174;|&#153;)","*").trim();
			str = str.replaceAll("\\s{2,}", " ");
		}
		return str;
	}
	
	/**
	 * Encode to URL - replace '+' with '%20'
	 * https://www.w3.org/TR/html4/interact/forms.html#h-17.13.4
	 * @param s
	 * @return
	 * @throws UnsupportedEncodingException
	 */
	public static String encodeUrl(String s) throws UnsupportedEncodingException {
		return URLEncoder.encode(s, "utf-8").replaceAll("\\+", "%20");
	}
	
	
	/**
	 * Test product detail page information
	 * @param myProducts
	 * @param ffBinaryPath
	 * @param siteUrl
	 */
	public static void productTest(MappingList myProducts, String ffBinaryPath, String siteUrl) {
		NumberFormat defaultFormat = NumberFormat.getPercentInstance();
		defaultFormat.setMinimumFractionDigits(1);
		
		WebDriver driver = null;
		if(ffBinaryPath.isEmpty()) {
			driver = new ChromeDriver();
		}else {
			driver = newFFDriver(ffBinaryPath); 
		}
		
		List<String> messageLog = new ArrayList<String>();
		String lastCatentryId = "";
		
		for(CatentryMapping cm : myProducts.getCatentryMapping()) {
			// progress %
			totalProducts++;
			if(totalProducts % 10 == 0 || totalProducts >= maxProducts) {
				System.out.println(defaultFormat.format((double)totalProducts/(double)maxProducts) + " (" + totalProducts + "/" + maxProducts + ")");
			}
			
			//grab necessary variables for testing
			String catentryId = cm.getId();
			
			//avoids repeat products
			if(lastCatentryId.equalsIgnoreCase(catentryId)) { continue; }
			lastCatentryId = catentryId;
			
			String productName = normalizeName(cm.getName());
			if(productName.equalsIgnoreCase("null")) {
				productName = "";
			}
			String productUrl = cm.getUrl();
			String shortDescription = null;
			String thumbnail = null;
			try {
				shortDescription = normalizeName(cm.getShortDescription());
			}catch(Exception e){}
			try {
				thumbnail = encodeUrl(cm.getImage().trim());
			}catch(Exception e){}
			String productType = cm.getType();
			
			String fullProductUrl = String.format("%s/%s",siteUrl, productUrl);
			
			try {
				driver.get(fullProductUrl);
				//delay first element check by 10 seconds to ensure page loads
				String aName = normalizeName((new WebDriverWait(driver, DEFAULT_WAIT_TIME)).until(ExpectedConditions.presenceOfElementLocated(By.className("productName"))).getText());
				if(!aName.equalsIgnoreCase(productName)) {
					messageLog.add(String.format("ERROR: %s - name mismatch - expected \"%s\" but found \"%s\"",catentryId, productName, aName));
				}
				
				if(productType.equalsIgnoreCase("finishes")) { continue; }
				
				if(thumbnail != null) {
					String aImgSrc = driver.findElement(By.className("productImage")).getAttribute("src");
					if(!aImgSrc.contains(thumbnail)) {
						messageLog.add(String.format("ERROR: %s - image mismatch - expected \"%s\" but found \"%s\"",productName, thumbnail, aImgSrc));
					}
				}
				
				if(productType.equalsIgnoreCase("decorative_accessories")) { continue; }
				
				if(shortDescription != null) {
					String aDesc = normalizeName(driver.findElement(By.className("productShortDescription")).getText());
					if(!aDesc.equalsIgnoreCase(shortDescription)) {
						messageLog.add(String.format("ERROR: %s - short description mismatch - expected \"%s\" but found \"%s\"",productName, shortDescription, aDesc));
					}
				}
			}catch(Exception e) {
				messageLog.add(String.format("ERROR: %s (%s) @ %s - %s", productName, catentryId, fullProductUrl, e.getMessage()));
			}
			
		}//for
		
		// count errors, add to totalErrors; save collected messages to log
		int foundErrors = messageLog.size();
		messageLog.add(String.format("Total errors found: %d", foundErrors));
		totalErrors += foundErrors;
		saveLog(messageLog, true);
		
		driver.quit();
	}//firefoxCabinetryTest
	
	/**
	 * Test add-to-cart functionality on product details page
	 * @param myProducts
	 * @param ffBinaryPath
	 * @param siteUrl
	 */
	public static void doorTest(MappingList myProducts, String ffBinaryPath, String siteUrl) {
		
		if(ffBinaryPath.isEmpty()) {
			webDriver = new ChromeDriver();
		}else {
			webDriver = newFFDriver(ffBinaryPath); 
		}
		
		String lastCatentryId = "";
		List<String> messageLog = new ArrayList<String>();
		
		for(CatentryMapping cm : myProducts.getCatentryMapping()) {
			//grab necessary variables for testing
			String catentryId = cm.getId();
			
			//avoids repeat products
			if(lastCatentryId.equalsIgnoreCase(catentryId)) {
				continue;
			}
			lastCatentryId = catentryId;
			
			String productName = normalizeName(cm.getName());
			String productUrl = cm.getUrl();
			String shortDescription = null;
			String thumbnail = null;
			try {
				shortDescription = normalizeName(cm.getShortDescription());
			}catch(Exception e){}
			try {
				thumbnail = encodeUrl(cm.getImage().trim());
			}catch(Exception e){}
			List<String> items = cm.getItemNames().getItemName();
			
			messageLog.add(String.format("%s - %d children",productName, items.size()));
			int fully_tested_skus = 0;
			
			String fullProductUrl = String.format("%s/%s",siteUrl, productUrl);
			
			try {
				//webDriver.get(String.format("%s/%s",siteUrl, productUrl));
				webDriver.navigate().to(fullProductUrl);
				
				//delay first element check by X seconds to ensure page loads
				String aName = normalizeName((new WebDriverWait(webDriver, DEFAULT_WAIT_TIME)).until(ExpectedConditions.presenceOfElementLocated(By.className("productName"))).getText());
				if(!aName.equalsIgnoreCase(productName)) {
					messageLog.add(String.format("ERROR: %s - name mismatch - expected \"%s\" but found \"%s\"",catentryId, productName, aName));
				}
				
				if(thumbnail != null) {
					String aImgSrc = webDriver.findElement(By.className("productImage")).getAttribute("src");
					if(!aImgSrc.contains(thumbnail)) {
						messageLog.add(String.format("ERROR: %s - image mismatch - expected \"%s\" but found \"%s\"",productName, thumbnail, aImgSrc));
					}
				}
				
				if(shortDescription != null) {
					String aDesc = webDriver.findElement(By.className("productShortDescription")).getText();
					if(!aDesc.equalsIgnoreCase(shortDescription)) {
						messageLog.add(String.format("ERROR: %s - short description mismatch - expected \"%s\" but found \"%s\"",productName, shortDescription, aDesc));
					}
				}
				
				// counts add-to-cart errors
				int repeat_offenses = 0;
				fully_tested_skus = 0;
				
				for(String item : items) {
					try {
						int cart_item_total = getCartQuantity(webDriver);
						
						// if full cart or max number of errors reached, close and re-open the browser and continue
						if(CART_CAP <= cart_item_total || repeat_offenses > MAX_OFFENSES) {
							webDriver.quit();
							if(ffBinaryPath.isEmpty()) {
								webDriver = new ChromeDriver();
							}else {
								webDriver = newFFDriver(ffBinaryPath); 
							}
							//webDriver.get(String.format("%s/%s",siteUrl, productUrl));
							webDriver.navigate().to(fullProductUrl);
							repeat_offenses = 0;
						}
						
						// find finish sku based on alt tag
						(new WebDriverWait(webDriver, DEFAULT_WAIT_TIME)).until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(String.format("img[alt='%s']",item))));
						WebElement finish = webDriver.findElement(By.cssSelector(String.format("img[alt='%s']",item)));
						String imgSrc = finish.getAttribute("src");
						imgSrc = imgSrc.substring(0, imgSrc.indexOf('?'));
						imgSrc = imgSrc.substring(imgSrc.lastIndexOf('/') + 1);
						
						// click with JS - bypass clicking the material
						JavascriptExecutor jsExecutor = (JavascriptExecutor)webDriver;
						jsExecutor.executeScript("arguments[0].click();",finish);
						
						// grab both add to cart buttons
						WebElement addChipToCartBtn = webDriver.findElement(By.id("chipSampleAddToCart"));
						WebElement addDoorToCartBtn = webDriver.findElement(By.id("doorSampleAddToCart"));
						
						// add finish chip to cart
						try {
							if(debugMismatch == 1) {
								addToCart(addChipToCartBtn, item, PRICE_3, imgSrc);
							}else {
								addToCart(addChipToCartBtn, item, PRICE_3);
							}
						} catch(Exception eee) {
							messageLog.add(String.format("ERROR: Broken chip sample - %s - %s", item, eee.getMessage()));
							repeat_offenses++;
						}
						
						// add door sample to cart
						try {
							addToCart(addDoorToCartBtn, item, PRICE_50);
						} catch(Exception eee) {
							messageLog.add(String.format("ERROR: Broken door sample - %s - %s", item, eee.getMessage()));
							repeat_offenses++;
						}
						
						fully_tested_skus++;
						
					}catch(Exception ee) {
						messageLog.add(String.format("ERROR: Broken SKU - %s - %s", item, ee.getMessage()));
					}
					
				}// for items
				
			}catch(Exception e) {
				messageLog.add(String.format("ERROR: %s (%s) @ %s - %s", productName, catentryId, fullProductUrl, e.getMessage()));
			}
			messageLog.add(String.format("Fully-tested children: %d/%d",fully_tested_skus, items.size()));
			// count errors, add to totalErrors; save collected messages to log
			int foundErrors = (messageLog.size() - 2);
			messageLog.add(String.format("Errors found: %d", foundErrors));
			totalErrors += foundErrors;
			totalProducts++;
			saveLog(messageLog, true);
			messageLog.clear();
			
		}// for CatentryMapping
		
		//close the driver
		webDriver.quit();
		
	}//firefoxCabinetryDoorTest
	
	/**
	 * Save list contents to file in folder
	 * @param folderName
	 * @param fileName
	 * @param contents
	 * @throws IOException
	 */
	public static void saveLog(List<String> contents, boolean append) {
		BufferedWriter writer = null;
		
		try {
			writer = new BufferedWriter(new FileWriter(resultLog, append));
			
			for(String s : contents) {
				writer.write(s);
				writer.newLine();
			}
			writer.close();
		} catch(IOException e) {
			System.out.printf("Could not create file. Total errors: %d\nError: %s", totalErrors, e.getMessage());
		}
	}//saveLog
}
