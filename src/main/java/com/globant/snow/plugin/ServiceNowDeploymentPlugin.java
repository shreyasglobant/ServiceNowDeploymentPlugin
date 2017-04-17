/**
 * 
 */
package com.globant.snow.plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

import javax.net.ssl.HttpsURLConnection;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.json.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * @author shreyas.gokodikar
 *
 */
@Mojo( name = "exporttosnow")
public class ServiceNowDeploymentPlugin extends AbstractMojo {

	/**
	 * @parameter
	 */
	@Parameter(property="exporttosnow.hostname", required=true)
	private String hostname;

	/**
	 * @parameter
	 */
	@Parameter(property="exporttosnow.username", required=true)
	private String username;

	/**
	 * @parameter
	 */
	@Parameter(property="exporttosnow.password", required=true)
	private String password;
	
	/**
     * Project's source directory as specified in the POM.
     * 
     * @parameter expression="${project.build.sourceDirectory}"
     * @readonly
     * @required
     */
	@Parameter(defaultValue="${project.build.sourceDirectory}/main/resources/server/scripted-rest-api", required=true)
    private File sourceDirectory;
	
	@Parameter(defaultValue="${project.build.sourceDirectory}/main/resources/server/index.json", required=true)
    private File indexFile;
	
    
    @Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		try {
			JSONArray restScriptsToUpdate = getScriptedRestApis();
            for (int i=0; i<=restScriptsToUpdate.size(); i++){
            	org.json.simple.JSONObject scriptedRestApi = (org.json.simple.JSONObject) restScriptsToUpdate.get(i);
    			JSONObject jsonObject = new JSONObject();
    			jsonObject.put("operation_script", new String(
    					Files.readAllBytes(
    							Paths.get(sourceDirectory+"/"+(String)scriptedRestApi.get("file")))));
        			
        		processAPICall(getApiConnection((String) scriptedRestApi.get("sysId")), jsonObject);
            }
		} catch (MalformedURLException e) {
			getLog().info("Invalid RestAPI URL.");
			getLog().info(e);
		} catch (IOException e) {
			getLog().info("Error reading index.json and/or script file not found." + e.getMessage());
			getLog().info(e);
			e.printStackTrace();
		} catch (ParseException e) {
			getLog().info("Error parsing index.json");
			getLog().info(e);
		} 
	}

	private void processAPICall(HttpsURLConnection connection,
			JSONObject jsonObject) throws IOException {
		getLog().info("in process api");
		OutputStream os = connection.getOutputStream();
		os.write(jsonObject.toString().getBytes());
		os.flush();
		os.close();
		
		int responseCode = connection.getResponseCode();
		getLog().info("PATCH Response Code :: " + responseCode);
		
		if (responseCode == HttpURLConnection.HTTP_OK) { //success
			BufferedReader in = new BufferedReader(new InputStreamReader(
					connection.getInputStream()));
			String inputLine;
			StringBuilder response = new StringBuilder();

			while ((inputLine = in.readLine()) != null) {
				response.append(inputLine);
			}
			in.close();

			// print result
			getLog().info(response.toString());
		} else {
			getLog().info("PATCH request not worked");
		}
		connection.disconnect();
	}

	private HttpsURLConnection getApiConnection(String sysId) throws IOException {
		System.getProperties().put("https.proxyHost", "proxy.corp.globant.com");
		System.getProperties().put("https.proxyPort", "3128");
		System.getProperties().put("http.proxyHost", "proxy.corp.globant.com");
		System.getProperties().put("http.proxyPort", "3128");
		URL url = new URL(hostname + "/api/now/table/sys_ws_operation/" + sysId);
		getLog().info(url.toString());
		HttpsURLConnection connection = (HttpsURLConnection) url
				.openConnection();
		connection.setRequestProperty("X-HTTP-Method-Override", "PATCH");
		connection.setRequestMethod("POST");
		connection.setRequestProperty("Content-Type", "application/json");
		connection.setRequestProperty("Accept", "application/json");
		String encoded = Base64.getEncoder().encodeToString(
				(username + ":" + password)
						.getBytes(StandardCharsets.UTF_8));
		getLog().info("Encode Password: Basic "+encoded);
		connection.setRequestProperty("Authorization", "Basic " + encoded);
		connection.setConnectTimeout(50000);
		connection.setReadTimeout(50000);
		connection.setDoOutput(true);
		connection.connect();
		return connection;
	}
	
	private JSONArray getScriptedRestApis() throws IOException, ParseException{
		
		JSONParser parser = new JSONParser();
		org.json.simple.JSONObject indexJsonObject = (org.json.simple.JSONObject)parser.parse(new FileReader(indexFile));
		return (JSONArray) indexJsonObject.get("scripted-rest-api");
	}

}
