package io.devalias.smartos;

import static com.google.appengine.api.urlfetch.FetchOptions.Builder.withDefaults;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.HttpURLConnection;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.*;

import com.google.appengine.labs.repackaged.org.json.JSONException;
import com.google.appengine.labs.repackaged.org.json.JSONObject;
import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPMethod;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.urlfetch.URLFetchService;
import com.google.appengine.api.urlfetch.URLFetchServiceFactory;


@SuppressWarnings("serial")
public class SmartServlet extends HttpServlet {
	public static final Logger logger = Logger.getLogger(SmartServlet.class.getName());
	public void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		resp.setContentType("text/plain");
		
		JSONObject  json = null;
		String		baseUrl = "http://lsa.schallwelle-muzik.net:9081";
		String		apiVer	= "api/0.1.0";
		String		apiCall = "vms";
		HTTPMethod	method	= HTTPMethod.GET;
		HTTPHeader	session = getSession(baseUrl, apiVer);
		String 		fullUrl = String.format("%s/%s/%s", baseUrl, apiVer, apiCall );
		logger.info("Returned from getSession()");
		
		
		if ( session != null)
			logger.info("session exists, URLFetch target: " + fullUrl);
			json = fetchURL(fullUrl, session, method);
		if (json != null) {
			logger.info("json response received, not null");
			resp.getWriter().write(json.toString());
		} else {
			logger.info("json response null.");
			resp.getWriter().write("We Got Nothin");
		}		
	}
	
	private HTTPHeader getSession(String baseUrl, String apiVer) {
		JSONObject loginJson = new JSONObject();
		String fullUrl = String.format("%s/%s/%s", baseUrl, apiVer, "sessions" );
		logger.info("Target Url, Session: " + fullUrl);
		
		logger.info("set login payload");
    	try {
    		loginJson.put("password", "gae808");
			loginJson.put("user", "gae");
			logger.info("login set.");
		} catch (JSONException e1) {
			// TODO Auto-generated catch block
			logger.log(Level.SEVERE, e1.getMessage(), e1);
		}

		try {
			logger.info("Attempting URlFetch to get session token");
			return fetchURL(fullUrl, loginJson, HTTPMethod.POST);  // this needs to come from an HTTPHeader.
		} catch (NullPointerException e) {
			// TODO Auto-generated catch block
			logger.log(Level.SEVERE, e.getMessage(), e);
		}
		return null;
	}

	private HTTPHeader fetchURL(String target, JSONObject loginJson, HTTPMethod method) {
		logger.info("entering fetchURL without session");
		try {
		    URL 			url = new URL(target);
		    URLFetchService urlFetchService = URLFetchServiceFactory.getURLFetchService();
		    HTTPRequest request = new HTTPRequest(url, method, withDefaults().setDeadline(30.00));
		    request.addHeader(new HTTPHeader("Content-Type", "application/json"));
		    request.addHeader(new HTTPHeader("accept", "application/json"));

		    if ( loginJson != null) {
			    request.setPayload(loginJson.toString().getBytes());
		    	logger.info("Payload Set");
		    } else {
		    	// bail out
		    	return null;
		    }
            HTTPResponse response = urlFetchService.fetch(request);
            
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                // OK
            	logger.info("HTTP response OK");
            	if (!response.getHeaders().isEmpty()){
            		logger.info("Total Headers: " + Integer.toString(response.getHeaders().size()));
            		for (Iterator<HTTPHeader> headers = response.getHeaders().iterator(); headers.hasNext();) {
            			HTTPHeader curHeader = headers.next();
            			logger.info("Header: " + curHeader.getName() + "\nValue: " + curHeader.getValue());
            			if (curHeader.getName().equals("x-snarl-token")) {
            				logger.info("Found x-snarl-token!");            				
            				return curHeader;
            			}
            		}
            	} else {
            		logger.info("response.getContent() was null\n"
            				+ response.toString());
            		return null;
            	}
            } else {
                // Server returned HTTP error code.
            	logger.info("Response Code NOT ok: " + response.getResponseCode());
            	return null;
            }		    
		} catch (MalformedURLException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		} catch (IOException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		}
		return null;
	}
	
	private JSONObject fetchURL(String target, HTTPHeader session, HTTPMethod method) {
		logger.info("Entering fetchURL with session");
		JSONObject json = null;
						
		try {
		    URL 			url = new URL(target);
		    URLFetchService urlFetchService = URLFetchServiceFactory.getURLFetchService();
		    HTTPRequest request = new HTTPRequest(url, method, withDefaults().setDeadline(30.00));
		    request.addHeader(new HTTPHeader("Content-Type", "application/json"));
		    request.addHeader(new HTTPHeader("accept", "application/json"));

		    if ( session != null ) {
		    	logger.info("Detected session. Setting header x-snarl-token.");
		    	request.addHeader(session);
		    	logger.info("Header set.");
		    } else {
		    	logger.info("No session. Aborting.");
		    	return null;
		    }
            
            HTTPResponse response = urlFetchService.fetch(request);
            
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                // OK
            	logger.info("HTTP response OK: " + response.getResponseCode());
            	logger.info("Response Headers: \n"
            			+ response.getHeaders().toString());
            	byte[] content = response.getContent();
            	if (content != null){
            		json = new JSONObject(content.toString());
        		    logger.info("Created JSONObject");
            	} else {
            		logger.info("response.getContent() was null\n"
            				+ response.toString());
            		return null;
            	}    			
            } else {
                // Server returned HTTP error code.
            	logger.info("Response Code NOT ok: " + response.getResponseCode());
            	return null;
            }		    
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return json;
	}
}
