package org.newrelic;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

public class Main {

    public static void main(String[] args) {

        // Need an API key for account 3770654
        String insightsAPIKey = "NRII-XXX";
        String insightsURL = "https://insights-collector.newrelic.com/v1/accounts/3770654/events";

        String nerdGraphAPIKey = "NRAK-XXX";
        String nerdGraphURL = "https://api.newrelic.com/graphql";

        try {
            JSONArray agentVersions = getAgentVersionData(nerdGraphURL, nerdGraphAPIKey);
            updateCharts(agentVersions, nerdGraphURL, nerdGraphAPIKey);
            uploadAgentVersionsData(agentVersions, insightsURL, insightsAPIKey);
        } catch (Exception e) {
            e.printStackTrace();
        }
     
    }


    // Get the agent version data from the New Relic GraphQL API
    private static JSONArray getAgentVersionData(String nerdGraphURL, String nerdGraphAPIKey ){
        List<String> agents = Arrays.asList("JAVA", "DOTNET", "RUBY", "PHP", "GO", "PYTHON", "NODEJS");

        HttpClient client = HttpClient.newHttpClient();

        // Calculate cutoff dates
        LocalDate ninetyDaysAgo = LocalDate.now().minusDays(90);
        LocalDate threeSixtyFiveDaysAgo = LocalDate.now().minusDays(365);
        LocalDate today = LocalDate.now();

        JSONArray agentVersions = new JSONArray();

        // Query the API for each agent
        for (String agent : agents) {
            String requestBody = String.format("{\"query\":\"{\\n  docs {\\n    agentReleases(agentName: %s) {\\n      date\\n      version\\n      features\\n      security\\n      bugs\\n    }\\n  }\\n}\", \"variables\":\"\"}", agent);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(nerdGraphURL))
                    .header("Content-Type", "application/json")
                    .header("API-Key", nerdGraphAPIKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                JSONObject jsonResponse = new JSONObject(response.body());
                JSONObject docs = jsonResponse.getJSONObject("data").getJSONObject("docs");
                JSONArray agentReleases = docs.getJSONArray("agentReleases");

                LocalDate oldestWithin90Days = null;
                String versionWithin90Days = null;

                LocalDate oldestWithin365Days = null;
                String versionWithin365Days = null;

                for (int i = 0; i < agentReleases.length(); i++) {
                    JSONObject release = agentReleases.getJSONObject(i);
                    LocalDate releaseDate = LocalDate.parse(release.getString("date"));
                    String version = release.getString("version");

                    // Check for the oldest within the last 90 days
                    if ((releaseDate.isAfter(ninetyDaysAgo) || releaseDate.isEqual(ninetyDaysAgo)) &&
                            (releaseDate.isBefore(today) || releaseDate.isEqual(today))) {

                        if (oldestWithin90Days == null || releaseDate.isBefore(oldestWithin90Days)) {
                            oldestWithin90Days = releaseDate;
                            versionWithin90Days = version;
                          
                        }
                    }

                    // Check for the oldest within the last 365 days
                    if ((releaseDate.isAfter(threeSixtyFiveDaysAgo) || releaseDate.isEqual(threeSixtyFiveDaysAgo)) &&
                            (releaseDate.isBefore(today) || releaseDate.isEqual(today))) {

                        if (oldestWithin365Days == null || releaseDate.isBefore(oldestWithin365Days)) {
                            oldestWithin365Days = releaseDate;
                            versionWithin365Days = version;
                        }
                    }

                }

                // Create a JSON object for the agent version
                JSONObject agentVersionJsonObject = new JSONObject();
                if (oldestWithin90Days != null) {
                    int[] majorMinor90 = parseVersion(versionWithin90Days);
                    System.out.printf("Agent: %s. Oldest release date <= 90 days old: %s, Version: %s (Major: %d, Minor: %d)%n",
                            agent, oldestWithin90Days, versionWithin90Days, majorMinor90[0], majorMinor90[1]);
                    agentVersionJsonObject.put("eventType", "AgentVersions");
                    agentVersionJsonObject.put("agent", agent.toLowerCase());
                    agentVersionJsonObject.put("releaseDate90", oldestWithin90Days);
                    agentVersionJsonObject.put("version90", versionWithin90Days);
                    agentVersionJsonObject.put("major90", majorMinor90[0]);
                    agentVersionJsonObject.put("minor90", majorMinor90[1]);
                } else {
                    System.out.println("Agent: " + agent + ". No release found <= 90 days old.");
                }

                if (oldestWithin365Days != null) {
                    int[] majorMinor365 = parseVersion(versionWithin365Days);
                    System.out.printf("Agent: %s. Oldest release date <= 365 days old: %s, Version: %s (Major: %d, Minor: %d)%n",
                            agent, oldestWithin365Days, versionWithin365Days, majorMinor365[0], majorMinor365[1]);
                            agentVersionJsonObject.put("eventType", "AgentVersions");
                            agentVersionJsonObject.put("agent", agent.toLowerCase());
                            agentVersionJsonObject.put("releaseDate365", oldestWithin365Days);
                            agentVersionJsonObject.put("version365", versionWithin365Days);
                            agentVersionJsonObject.put("major365", majorMinor365[0]);
                            agentVersionJsonObject.put("minor365", majorMinor365[1]);
                } else {
                    System.out.println("Agent: " + agent + ". No release found <= 365 days old.");
                }

                // Add the agent version to the JSON array
                agentVersions.put(agentVersionJsonObject);

            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        return agentVersions;
    }

    // Parse the major and minor version numbers from the version string
    private static int[] parseVersion(String version) {
        String[] parts = version.split("\\.");
        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        return new int[]{major, minor};
    }

    // Upload the agent version data to New Relic Insights (Custom Events)
    private static void uploadAgentVersionsData(JSONArray agentVersions, String url, String apiKey){
 
        //String apiUrl = "https://insights-collector.newrelic.com/v1/accounts/3770654/events";
        postJsonData(agentVersions.toString().getBytes(), url, apiKey);
    }

    // Post JSON data to New Relic
    private static void postJsonData(byte[] data, String apiUrl, String apiKey) {
        //byte[] data = jsonArray.toString().getBytes();

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("Api-Key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(data))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("Response code: " + response.statusCode());
            System.out.println("Response body: " + response.body());

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void updateCharts(JSONArray agentVersions, String url, String apiKey) {

        String allAgents90Template = "WITH  numeric(capture(agentVersion, r'(?P<majorVersion>[\\\\d]+)\\\\..*')) as major, numeric(capture(agentVersion, r'\\\\d+\\\\.(?P<minorVersion>\\\\d+)\\\\..*')) as minor " + //
                "FROM GoMetadataSummary, PhpMetadataSummary, PythonMetadataSummary, RubyMetadataSummary, NodeMetadataSummary, DotnetMetadataSummary, JvmMetadataSummary " + //
                "SELECT percentage( uniqueCount(applicationId ), " + //
                "    where " + //
                "        (eventType() = 'GoMetadataSummary' and ((major = major90_go and minor >= minor90_go) OR major > major90_go )) OR " + //
                "        (eventType() = 'PhpMetadataSummary' and ((major = major90_php and minor >= minor90_php) OR major > major90_php )) OR " + //
                "        (eventType() = 'PythonMetadataSummary' and ((major = major90_python and minor >= minor90_python) OR major > major90_python )) OR " + //
                "        (eventType() = 'RubyMetadataSummary' and ((major = major90_ruby and minor >= minor90_ruby) OR major > major90_ruby )) OR " + //
                "        (eventType() = 'NodeMetadataSummary'  and ((major = major90_nodejs and minor >= minor90_nodejs) OR major > major90_nodejs )) OR " + //
                "        (eventType() = 'DotnetMetadataSummary' and ((major = major90_dotnet and minor >= minor90_dotnet) OR major > major90_dotnet )) OR " + //
                "        (eventType() = 'JvmMetadataSummary' and ((major = major90_java and minor >= minor90_java) OR major > major90_java )) " + //
                "    ) " + //
                "SINCE 1 week ago ";

        String allAgents90breakoutTemplate = "WITH  numeric(capture(agentVersion, r'(?P<majorVersion>[\\\\d]+)\\\\..*')) as major, numeric(capture(agentVersion, r'\\\\d+\\\\.(?P<minorVersion>\\\\d+)\\\\..*')) as minor " + //
                "FROM GoMetadataSummary, PhpMetadataSummary, PythonMetadataSummary, RubyMetadataSummary, NodeMetadataSummary, DotnetMetadataSummary, JvmMetadataSummary  " + //
                "SELECT percentage( uniqueCount(applicationId ),  " + //
                "    where  " + //
                "         (eventType() = 'GoMetadataSummary' and ((major = major90_go and minor >= minor90_go) OR major > major90_go )) OR " + //
                "        (eventType() = 'PhpMetadataSummary' and ((major = major90_php and minor >= minor90_php) OR major > major90_php )) OR " + //
                "        (eventType() = 'PythonMetadataSummary' and ((major = major90_python and minor >= minor90_python) OR major > major90_python ))  OR " + //
                "        (eventType() = 'RubyMetadataSummary' and ((major = major90_ruby and minor >= minor90_ruby) OR major > major90_ruby )) OR " + //
                "        (eventType() = 'NodeMetadataSummary'  and ((major = major90_nodejs and minor >= minor90_nodejs) OR major > major90_nodejs )) OR " + //
                "        (eventType() = 'DotnetMetadataSummary' and ((major = major90_dotnet and minor >= minor90_dotnet) OR major > major90_dotnet )) OR " + //
                "        (eventType() = 'JvmMetadataSummary' and ((major = major90_java and minor >= minor90_java) OR major > major90_java )) " + //
                "    ) FACET eventType()  " + //
                "SINCE 1 week ago ";

        String allAgents365Template = "WITH  numeric(capture(agentVersion, r'(?P<majorVersion>[\\\\d]+)\\\\..*')) as major, numeric(capture(agentVersion, r'\\\\d+\\\\.(?P<minorVersion>\\\\d+)\\\\..*')) as minor " + //
                "FROM GoMetadataSummary, PhpMetadataSummary, PythonMetadataSummary, RubyMetadataSummary, NodeMetadataSummary, DotnetMetadataSummary, JvmMetadataSummary  " + //
                "SELECT percentage( uniqueCount(applicationId ),  " + //
                "    where  " + //
                "         (eventType() = 'GoMetadataSummary' and ((major = major365_go and minor >= minor365_go) OR major > major365_go )) OR " + //
                "        (eventType() = 'PhpMetadataSummary' and ((major = major365_php and minor >= minor365_php) OR major > major365_php )) OR " + //
                "        (eventType() = 'PythonMetadataSummary' and ((major = major365_python and minor >= minor365_python) OR major > major365_python ))  OR " + //
                "        (eventType() = 'RubyMetadataSummary' and ((major = major365_ruby and minor >= minor365_ruby) OR major > major365_ruby )) OR " + //
                "        (eventType() = 'NodeMetadataSummary'  and ((major = major365_nodejs and minor >= minor365_nodejs) OR major > major365_nodejs )) OR " + //
                "        (eventType() = 'DotnetMetadataSummary' and ((major = major365_dotnet and minor >= minor365_dotnet) OR major > major365_dotnet )) OR " + //
                "        (eventType() = 'JvmMetadataSummary' and ((major = major365_java and minor >= minor365_java) OR major > major365_java )) " + //
                "    ) " + //
                "SINCE 1 week ago ";

        String allAgents365breakoutTemplate = "WITH  numeric(capture(agentVersion, r'(?P<majorVersion>[\\\\d]+)\\\\..*')) as major, numeric(capture(agentVersion, r'\\\\d+\\\\.(?P<minorVersion>\\\\d+)\\\\..*')) as minor " + //
                "FROM GoMetadataSummary, PhpMetadataSummary, PythonMetadataSummary, RubyMetadataSummary, NodeMetadataSummary, DotnetMetadataSummary, JvmMetadataSummary  " + //
                "SELECT percentage( uniqueCount(applicationId ),  " + //
                "    where  " + //
                "         (eventType() = 'GoMetadataSummary' and ((major = major365_go and minor >= minor365_go) OR major > major365_go )) OR " + //
                "        (eventType() = 'PhpMetadataSummary' and ((major = major365_php and minor >= minor365_php) OR major > major365_php )) OR " + //
                "        (eventType() = 'PythonMetadataSummary' and ((major = major365_python and minor >= minor365_python) OR major > major365_python ))  OR " + //
                "        (eventType() = 'RubyMetadataSummary' and ((major = major365_ruby and minor >= minor365_ruby) OR major > major365_ruby )) OR " + //
                "        (eventType() = 'NodeMetadataSummary'  and ((major = major365_nodejs and minor >= minor365_nodejs) OR major > major365_nodejs )) OR " + //
                "        (eventType() = 'DotnetMetadataSummary' and ((major = major365_dotnet and minor >= minor365_dotnet) OR major > major365_dotnet )) OR " + //
                "        (eventType() = 'JvmMetadataSummary' and ((major = major365_java and minor >= minor365_java) OR major > major365_java )) " + //
                "    ) FACET eventType()  " + //
                "SINCE 1 week ago ";

        String allAgents90 = "{\"query\":\"mutation {\\n" +
                        "  dashboardUpdateWidgetsInPage(\\n" +
                        "    widgets: {id: 368806745, configuration: {billboard: {nrqlQueries: {accountId: 1, query: \\\"" + replaceAgentVersions90(agentVersions, allAgents90Template) + "\\\"}}}, title: \\\"Agents on versions w/in 90 days (all)\\\", layout: {column: 1, height: 5, row: 2, width: 4}}}\\n" +
                        "    guid: \\\"MXxWSVp8REFTSEJPQVJEfDMzNzMyNjA3\\\"\\n" +
                        "  ) {\\n" +
                        "    errors {\\n" +
                        "      description\\n" +
                        "    }\\n" +
                        "  }\\n" +
                        "}\", \"variables\":\"\"}";

        System.out.println(allAgents90);

        postJsonData(allAgents90.getBytes(), url, apiKey);  
        
        String allAgents90Breakout = "{\"query\":\"mutation {\\n" +
                        "  dashboardUpdateWidgetsInPage(\\n" +
                        "    widgets: {id: 368806746, configuration: {bar: {nrqlQueries: {accountId: 1, query: \\\"" + replaceAgentVersions90(agentVersions, allAgents90breakoutTemplate) + "\\\"}}}, title: \\\"Agents on versions w/in 90 days (breakout)\\\", layout: {column: 5, height: 5, row: 2, width: 8}}\\n" +
                        "    guid: \\\"MXxWSVp8REFTSEJPQVJEfDMzNzMyNjA3\\\"\\n" +
                        "  ) {\\n" +
                        "    errors {\\n" +
                        "      description\\n" +
                        "    }\\n" +
                        "  }\\n" +
                        "}\", \"variables\":\"\"}";

        System.out.println(allAgents90Breakout);

        postJsonData(allAgents90Breakout.getBytes(), url, apiKey);  
        
        String allAgents365 = "{\"query\":\"mutation {\\n" +
                    "  dashboardUpdateWidgetsInPage(\\n" +
                    "    widgets: {id: 368884716, configuration: {billboard: {nrqlQueries: {accountId: 1, query: \\\"" + replaceAgentVersions365(agentVersions, allAgents365Template) + "\\\"}}}, title: \\\"Agents on versions w/in 365 days (all)\\\", layout: {column: 1, height: 5, row: 7, width: 4}}}\\n" +
                    "    guid: \\\"MXxWSVp8REFTSEJPQVJEfDMzNzMyNjA3\\\"\\n" +
                    "  ) {\\n" +
                    "    errors {\\n" +
                    "      description\\n" +
                    "    }\\n" +
                    "  }\\n" +
                    "}\", \"variables\":\"\"}";

        System.out.println(allAgents365);

        postJsonData(allAgents365.getBytes(), url, apiKey);  

        String allAgents365Breakout = "{\"query\":\"mutation {\\n" +
                "  dashboardUpdateWidgetsInPage(\\n" +
                "    widgets: {id: 368884719, configuration: {bar: {nrqlQueries: {accountId: 1, query: \\\"" + replaceAgentVersions365(agentVersions, allAgents365breakoutTemplate) + "\\\"}}}, title: \\\"Agents on versions w/in 365 days (breakout)\\\", layout: {column: 5, height: 5, row: 7, width: 8}}\\n" +
                "    guid: \\\"MXxWSVp8REFTSEJPQVJEfDMzNzMyNjA3\\\"\\n" +
                "  ) {\\n" +
                "    errors {\\n" +
                "      description\\n" +
                "    }\\n" +
                "  }\\n" +
                "}\", \"variables\":\"\"}";

        System.out.println(allAgents365Breakout);

        postJsonData(allAgents365Breakout.getBytes(), url, apiKey);   

    }


    private static String replaceAgentVersions90(JSONArray agentVersions, String allAgents90Template) {
        for (int i = 0; i < agentVersions.length(); i++) {
            JSONObject agentVersionJsonObject = agentVersions.getJSONObject(i);
            String agent = agentVersionJsonObject.getString("agent");
            int major90 = agentVersionJsonObject.optInt("major90", 99);
            int minor90 = agentVersionJsonObject.optInt("minor90", 99);
            allAgents90Template = allAgents90Template.replace("major90_" + agent, String.valueOf(major90));
            allAgents90Template = allAgents90Template.replace("minor90_" + agent, String.valueOf(minor90));
        }
        return allAgents90Template;
    }

    private static String replaceAgentVersions365(JSONArray agentVersions, String allAgents365Template) {
        for (int i = 0; i < agentVersions.length(); i++) {
            JSONObject agentVersionJsonObject = agentVersions.getJSONObject(i);
            String agent = agentVersionJsonObject.getString("agent");
            int major365 = agentVersionJsonObject.optInt("major365", 99);
            int minor365 = agentVersionJsonObject.optInt("minor365", 99);
            allAgents365Template = allAgents365Template.replace("major365_" + agent, String.valueOf(major365));
            allAgents365Template = allAgents365Template.replace("minor365_" + agent, String.valueOf(minor365));
        }
        return allAgents365Template;
    }
}