package io.devalias.smartos;

import static com.google.appengine.api.urlfetch.FetchOptions.Builder.withDefaults;

import java.io.DataOutputStream;
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
		HTTPHeader	session = getSession(baseUrl, apiVer); // Snarl's session token is send via header
		String 		fullUrl = String.format("%s/%s/%s", baseUrl, apiVer, apiCall );
		logger.info("Returned from getSession()");

		if ( session != null)
			logger.info("session exists, URLFetch target: " + fullUrl);
			json = fetchURL(fullUrl, session, method);

		if (json != null) {
			logger.info("json response received, not null");
			String output = "nothing!";
			try {
				output = (String) json.get("vm").toString();
			} catch (JSONException e) {
				logger.log(Level.SEVERE, e.getMessage(), e);
			}
			String[] vms = output.replace("[", "").replace("]", "").replace("\"", "").split(",");
			JSONObject vminfo = null;
			String vmUrl = fullUrl;
			for ( String uuid : vms) {
				logger.info("Processing UUID " + uuid);
				resp.getWriter().write("VM UUID: " + uuid + "\n");
				vmUrl = String.format("%s/%s", fullUrl, uuid);
				logger.info("Target URL: " + vmUrl);
				vminfo = fetchURL(vmUrl, session, method);
				try {
					String value;
					for (Iterator iter = vminfo.keys(); iter.hasNext();) {
						value = vminfo.getString(key);
						resp.getWriter().write(String.format("%s: %s", key, value) + "\n");
					}
				} catch (JSONException e) {
					logger.log(Level.SEVERE, e.getMessage(), e);
				} catch (NullPointerException e) {
					logger.info("getNames response null for " + uuid);
					logger.log(Level.SEVERE, e.getMessage(), e);
				}
			}
		} else {
			logger.info("json response null.");
			resp.getWriter().write("We Got Nothin");
		}
	}

	// prepare to get a session
	private HTTPHeader getSession(String baseUrl, String apiVer) {
		JSONObject loginJson = new JSONObject();
		String fullUrl = String.format("%s/%s/%s", baseUrl, apiVer, "sessions" );
		URL url;
		HttpURLConnection connection;
		try {
			url = new URL(fullUrl);
			connection = (HttpURLConnection) url.openConnection();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			logger.log(Level.SEVERE, e1.getMessage(), e1);
			return null;
		}

		logger.info("Target Url, Session: " + fullUrl);
		logger.info("set login payload");
		try {
			loginJson.put("password", "gae808");
			loginJson.put("user", "gae");
			logger.info("Login payload set.");
		} catch (JSONException e1) {
			// TODO Auto-generated catch block
			logger.log(Level.SEVERE, e1.getMessage(), e1);
		}

		try {
			logger.info("Attempting URlFetch to get session token");
			return fetchURL(connection, loginJson, HTTPMethod.POST);  // this needs to come from an HTTPHeader.
		} catch (NullPointerException e) {
			// TODO Auto-generated catch block
			logger.log(Level.SEVERE, e.getMessage(), e);
		}
		return null;
	}

	// URLFetch call to get a valid session token
	private HTTPHeader fetchURL(HttpURLConnection connection, JSONObject urlParameters, HTTPMethod method) {
		logger.info("entering fetchURL without session");
		try {
			logger.info("set request method and headers");
		    connection.setRequestMethod(method.name());
		    connection.setRequestProperty("Accept", "application/json");
		    connection.setRequestProperty("Content-Type", "application/json");
		    connection.setDoOutput(true);
		    connection.setDoInput(true);
		    connection.setInstanceFollowRedirects(false);
		    logger.info("set POST parameters");
		    DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
		     wr.writeBytes(urlParameters.toString());
		     wr.flush();
		     wr.close();
		    
            int code = connection.getResponseCode();
            logger.info("Check response code.");
            if (code == HttpURLConnection.HTTP_OK || code == HttpURLConnection.HTTP_SEE_OTHER) {
                // OK
            	logger.info("HTTP response OK: " + code);
            	String token = connection.getHeaderFields().get("x-snarl-token").get(0);

    			if (token != null) {
    				logger.info("Found x-snarl-token!");
    				HTTPHeader snarl = new HTTPHeader("x-snarl-token", token);
    				return snarl;
    			} else {
            		logger.info("No x-snarl-token found.");
            		return null;
            	}
            } else {
                // Server returned HTTP error code.
            	logger.info("Response Code NOT ok: " + code);
            	return null;
            }		    
		} catch (MalformedURLException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		} catch (IOException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		}
		return null;
	}

	// URLFetch call with a valid session token
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
            	for (HTTPHeader header : response.getHeaders() )
        			logger.info("Header: " + header.getName() + "\nValue: " + header.getValue());
            	String content = new String(response.getContent());
            	logger.info("Response Content received: " + content);

            	//logger.info("Response Content: " + uuids);
            	json = new JSONObject();
            	json.put("vm", content);
        		logger.info("Created JSONObject");
            } else {
                // Server returned HTTP error code.
            	logger.info("Response Code NOT ok: " + response.getResponseCode());
            	return null;
            }
		} catch (MalformedURLException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		} catch (IOException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		} catch (JSONException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		}
		return json;
	}
}
