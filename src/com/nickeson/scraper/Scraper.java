package com.nickeson.scraper;

//JDK 1.8.0
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.ElementIterator;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.parser.ParserDelegator;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;

// log4j-1.2.17.jar
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

/****************************************************************************
 * <b>Title</b>: Scraper.java <p/>
 * <b>Project</b>: SMT Site Menu Scraper <p/>
 * <b>Description: </b>A website scraper to locally save content 
 * (HTML, .css, .js, etc.) and header information for all pages found in the 
 * Siliconmtn.com header and footer menu links, while retaining absolute links 
 * to other content (images, audio, video, etc.)<p/>
 * <b>Copyright:</b> Copyright (c) 2016<p/>
 * <b>Company:</b> Silicon Mountain Technologies<p/>
 * @author nickeson
 * @version 1.0
 * @since Jun 22, 2016<p/>
 * updates:
 ****************************************************************************/

public class Scraper {
	protected static Logger log = null;
	private Properties config = null;
	private HttpURLConnection baseHttpConnection = null;
	private int initCnxnStatus = 0;
	private	String jSessionID = null;
	public static final String ACCEPT_CHARSET = "acceptCharset";
	public static final String BASE_DOMAIN = "baseDomain";
	public static final String BASE_PORT = "basePort";
	public static final String LOG4J_LOCATION = "log4Jlocation";	
	public static final String MENU_PARSE_STRING = "menuParseString";
	public static final String SESSIONID_PARSE_STRING = "sessionIdParseString";
	public static final String OUTPUT_DIRECTORY = "outputDirectory";

	/**
	 * default constructor needs a predefined properties file to configure Scraper
	 */
	public Scraper() {
		try {
			FileInputStream fileInput = new FileInputStream("scripts/scraper.properties");
			config = new Properties();
			config.load(fileInput);
			fileInput.close();
			PropertyConfigurator.configure(config.getProperty(LOG4J_LOCATION));
			log = Logger.getLogger("Scraper");
			baseHttpConnection = (HttpURLConnection)urlBuilder(
				config.getProperty(BASE_DOMAIN) + ":" 
				+ config.getProperty(BASE_PORT)).openConnection();
			initCnxnStatus = baseHttpConnection.getResponseCode();
		} catch (FileNotFoundException e) {
			log.error("Properties File Not Found");
			e.printStackTrace();
		} catch (MalformedURLException mue) {
			log.error("URL is invalid/malformed");
			mue.printStackTrace();
		} catch (IOException e) {
			log.error("Cannot load or close Properties File or baseHttpConnection");
			e.printStackTrace();
		} 
	}

	/**
	 * Build a URL object from a URL String
	 * @param urlString a String containing the URL to build into a URL Object
	 * @return the URL Object generated from URL String
	 */
	public URL urlBuilder(String urlString) {
		URL outURL = null;
		try {
			outURL = new URL(urlString);
		} catch (MalformedURLException e) {
			log.error("URL is invalid/malformed");
			e.printStackTrace();
		} return outURL;
	}
	
	/**
	 * Uses a seed URL (via HttpURLConnection Object) to generate a list of Links
	 * contained in the seed URL
	 * @param connection the HttpURLConnection Object to use to generate the List of Links
	 * @return a LinkedHashSet of Links contained in the HttpURLConnection Object
	 */
	public Set<String> buildLinksList(URL url) {
		Set<String> linksList = new LinkedHashSet<>();
		HTMLEditorKit.Parser parser = new ParserDelegator();
		try {
			HttpURLConnection connection = (HttpURLConnection)url.openConnection();
			if (connection.getResponseCode() == 200) {
				Reader in = new InputStreamReader(connection.getInputStream());
				parser.parse(in, new HTMLEditorKit.ParserCallback() {
					public void handleStartTag(HTML.Tag t, MutableAttributeSet a, int pos) {
						if (t == HTML.Tag.A) {
							Object link = a.getAttribute(HTML.Attribute.HREF);
							if (link != null) linksList.add(String.valueOf(link)); 
						}
					}
				}, true);
				in.close();
			} else log.error("Status Code: " + connection.getResponseCode()); 
		} catch (IOException ioe) {
			log.error("Unable to store data - connection or filesystem errors");
			ioe.printStackTrace();
		} return linksList;
	}
	
	/**
	 * Extracts all resource links from an HttpURLConnection Object to a unique Set
	 * @param connection the HttpURLConnection to extract resource links from
	 * @return a LinkedHashSet of resource Links contained in the HttpURLConnection Object
	 */
	public Set<String> buildResourcesList(URL url) {
		Set<String> resourceList = new LinkedHashSet<>();
		try {
			HttpURLConnection connection = (HttpURLConnection)url.openConnection();
			if (connection.getResponseCode() == 200) {
				Element elem = null;
				Reader in = new InputStreamReader(connection.getInputStream());
				HTMLEditorKit kit = new HTMLEditorKit();
				HTMLDocument doc = (HTMLDocument) kit.createDefaultDocument();
				doc.putProperty("IgnoreCharsetDirective",  Boolean.TRUE);
				ElementIterator it = new ElementIterator(doc);
				kit.read(in, doc, 0);
				while ((elem = it.next()) != null) {
					String s = (String)elem.getAttributes().getAttribute(HTML.Attribute.SRC);
					if (s != null) {
						resourceList.add(s);
					}
				} in.close();
			} else log.error("Status Code: " + connection.getResponseCode()); 
		} catch (BadLocationException e) {
			log.error("Unable to read from InputStream");
			e.printStackTrace();
		} catch (IOException ioe) {
			log.error("Unable to store data - connection or filesystem errors");
			ioe.printStackTrace();
		} return resourceList;
	}

	/**
	 * Store JSESSIONID cookie value
	 */
	public void storeSessionIdCookie() {
		List<String> rawCookies = baseHttpConnection.getHeaderFields().get("Set-Cookie");
		if (initCnxnStatus == 200) {
			for (String rawCookie : rawCookies) {
				String[] cookies = rawCookie.split("; ");
				String cookie = cookies[0];
				String[] cookieValues = cookie.split("=");
				String cookieName = cookieValues[0];
				String cookieValue = cookieValues[1];
				if (cookieName.equalsIgnoreCase(
						config.getProperty(SESSIONID_PARSE_STRING))) jSessionID = cookieValue;
			}
			if (jSessionID == null) log.error("Can't store " + config.getProperty(SESSIONID_PARSE_STRING)); 
		}
	}
	
	/**
	 * Sets SESSIONID cookie
	 * @param url the URL to which to pass the cookie
	 * @param cookie the cookie to pass to the URL
	 */
	public void setSessionIdCookie(URL url, String cookie) {
		try {
			HttpURLConnection connection = (HttpURLConnection)url.openConnection();
			connection.setRequestProperty(config.getProperty(SESSIONID_PARSE_STRING), cookie);
			connection.connect();
		} catch (IOException ioe) {
			log.error("Unable to connect to " + url + " to set SessionIdCookie");
			ioe.printStackTrace();
		}
	}

	/**
	 * Store all valid connection stream data to specified file
	 * @param url the URL of the data to store
	 * @param file the location to store the stream data
	 */
	public void storeURLData(URL url, String file) {
		try {
			HttpURLConnection connection = (HttpURLConnection)url.openConnection();
			if (connection.getResponseCode() == 200) {
				BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
				BufferedWriter out = new BufferedWriter(new FileWriter(new File(file)));
				String inputLine = null;
				while ((inputLine = in.readLine()) != null) {
					out.write(inputLine + "\n");
				} in.close(); out.close(); connection.disconnect();  
			} else log.error("Status Code: " + connection.getResponseCode()); 
		} catch (IOException ioe) {
			log.error("Unable to store data - connection or filesystem errors");
			ioe.printStackTrace();
		}
	}
	
	// unit test
	public static void main(String[] args) {
		Scraper scraper = new Scraper();
		scraper.storeSessionIdCookie(); System.out.println(scraper.jSessionID); 

		URL workingURL = scraper.urlBuilder(scraper.config.getProperty(BASE_DOMAIN) + ":" 
			+ scraper.config.getProperty(BASE_PORT));

		System.out.println("LinksList: " + scraper.buildLinksList(workingURL));
		System.out.println("ResourcesList: " + scraper.buildResourcesList(workingURL));

		scraper.storeURLData(workingURL, scraper.config.getProperty(OUTPUT_DIRECTORY) + "index.html");
		scraper.baseHttpConnection.disconnect();
	}
}