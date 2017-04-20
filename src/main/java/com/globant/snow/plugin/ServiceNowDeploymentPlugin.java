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
import org.json.JSONException;
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
	 * Configuration parameter service now host details.
	 * @parameter
	 */
	@Parameter(property="exporttosnow.hostname", required=true)
	private String hostname;

	/**
	 * Configuration parameter service now user name.
	 * @parameter
	 */
	@Parameter(property="exporttosnow.username", required=true)
	private String username;

	/**
	 * Configuration parameter service now password.
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
	
	@Parameter(defaultValue="${project.build.sourceDirectory}/main/resources/server/script-include", required=true)
    private File includeScriptDirectory;
	
	@Parameter(defaultValue="${project.build.sourceDirectory}/main/resources/server/index.json", required=true)
    private File indexFile;
	
	@Parameter(defaultValue="${project.build.sourceDirectory}/main/resources/server/table", required=true)
    private File fileDirectory;
	
	@Parameter(defaultValue="${project.build.sourceDirectory}/main/resources/client/dist", required=true)
    private File uiFileDirectory;
	
	private static final String PATCH_ACTION = "PATCH";
	private static final String POST_ACTION = "POST";
	private static final String DELETE_ACTION = "DELETE";
	
	private org.json.simple.JSONObject indexJsonObject;
	
	private final JSONParser parser = new JSONParser();
    
    @Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		try {
			indexJsonObject = (org.json.simple.JSONObject)parser.parse(new FileReader(indexFile));
			processScriptedRestApis();
			processScriptIncludeMappings();
			processTableMappings();
			processScriptUIFiles();
			
		} catch (MalformedURLException e) {
			getLog().info("Invalid RestAPI URL.");
			getLog().info(e);
		} catch (IOException e) {
			getLog().info("Error reading index.json and/or script file not found." + e.getMessage());
			getLog().info(e);
		} catch (ParseException e) {
			getLog().info("Error parsing index.json");
			getLog().info(e);
		} 
	}

	private void processAPICall(HttpsURLConnection connection,
			String jsonObject) throws IOException {
		OutputStream os = connection.getOutputStream();
		os.write(jsonObject.getBytes());
		os.flush();
		os.close();
		
		int responseCode = connection.getResponseCode();
		getLog().info("Service now Response Code :: " + responseCode);
		
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
			getLog().info("Service now request not worked");
		}
		connection.disconnect();
	}

	private HttpsURLConnection getApiConnection(String api, String action) throws IOException {
		//Proxy settings for Globant Network.
		System.getProperties().put("https.proxyHost", "proxy.corp.globant.com");
		System.getProperties().put("https.proxyPort", "3128");
		System.getProperties().put("http.proxyHost", "proxy.corp.globant.com");
		System.getProperties().put("http.proxyPort", "3128");
		URL url = new URL(hostname + api);
		getLog().info(url.toString());
		HttpsURLConnection connection = (HttpsURLConnection) url
				.openConnection();
		if(PATCH_ACTION.equalsIgnoreCase(action)){
			connection.setRequestProperty("X-HTTP-Method-Override", PATCH_ACTION);
			connection.setRequestMethod(POST_ACTION);
		}else{
			connection.setRequestMethod(action);
		}
		
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
	
	private void processScriptedRestApis() throws IOException{
		JSONArray restScriptsToUpdate = (JSONArray) indexJsonObject.get("scripted-rest-api");
		updateServiceNow(restScriptsToUpdate, "/api/now/table/sys_ws_operation/", PATCH_ACTION, "operation_script", sourceDirectory);
	}

	/**
	 * @param restScriptsToUpdate
	 * @throws IOException 
	 * @throws JSONException 
	 */
	private void updateServiceNow(JSONArray restScriptsToUpdate, String api, String action, String columnToUpdate, File source) throws IOException {
		for (Object scriptedRestApiObj : restScriptsToUpdate){
			org.json.simple.JSONObject scriptedRestApi = (org.json.simple.JSONObject) scriptedRestApiObj;
			JSONObject jsonObject = new JSONObject();
			jsonObject.put(columnToUpdate, new String(
					Files.readAllBytes(
							Paths.get(source+"/"+(String)scriptedRestApi.get("file")))));
    		processAPICall(getApiConnection(api+(String) scriptedRestApi.get("sysId"), action), jsonObject.toString());
        }
	}
	
	private void processScriptUIFiles()throws IOException{
		JSONArray uiScriptsToUpdate = (JSONArray) indexJsonObject.get("UI-Script");
		updateServiceNow(uiScriptsToUpdate, "/api/now/table/sys_ui_script/", PATCH_ACTION, "script", uiFileDirectory);
		
		JSONArray uiPageToUpdate = (JSONArray) indexJsonObject.get("UI-Html");
		updateServiceNow(uiPageToUpdate, "/api/now/table/sys_ui_page/", PATCH_ACTION, "html", uiFileDirectory);
	
	
	}
	
	private void processScriptIncludeMappings()throws IOException{
		JSONArray restScriptsToUpdate = (JSONArray) indexJsonObject.get("script-include");
		updateServiceNow(restScriptsToUpdate, "/api/now/table/sys_script_include/", PATCH_ACTION, "script", includeScriptDirectory);
	}
	
	private void processTableMappings()throws IOException, ParseException{
		String action = POST_ACTION;
		JSONArray restScriptsToUpdate = (JSONArray) indexJsonObject.get("table");
		for (Object scriptedRestApiObj : restScriptsToUpdate){
			org.json.simple.JSONObject scriptedRestApi = (org.json.simple.JSONObject) scriptedRestApiObj;
        	JSONArray fileNames = (JSONArray)scriptedRestApi.get("file");
        	for(Object fileNameObj: fileNames){
        		String filename = (String) fileNameObj;
        		org.json.simple.JSONObject jsonFileData = (org.json.simple.JSONObject)parser.parse(new FileReader(fileDirectory+"/"+filename));
        		if("update".equalsIgnoreCase((String)jsonFileData.get("mode"))){
        			action = PATCH_ACTION;
        		}else if("delete".equalsIgnoreCase((String)jsonFileData.get("mode"))){
        			action = DELETE_ACTION;
        		}
        		org.json.simple.JSONObject object = (org.json.simple.JSONObject)jsonFileData.get("values");
        		processAPICall(getApiConnection("/api/now/table/"+(String) scriptedRestApi.get("name"), action), object.toJSONString());
        	}
			
        }
	}
}
