package testflight;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.simple.parser.JSONParser;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Scanner;
import org.apache.commons.lang.builder.ToStringBuilder;

/**
 * A testflight uploader
 */
public class TestflightUploader implements Serializable {
    static interface Logger {
        void logDebug(String message);
    }

    static class UploadRequest implements Serializable
    {
        String filePath;
        String dsymPath;
        String apiToken;
        String teamToken;
        Boolean notifyTeam;
        String buildNotes;
        File file;
        File dsymFile;
        String lists;
        Boolean replace;
        String proxyHost;
        String proxyUser;
        String proxyPass;
        int proxyPort;
        Boolean debug;

        public String toString() {
            return new ToStringBuilder(this)
                .append("filePath", filePath)
                .append("dsymPath", dsymPath)
                .append("apiToken", "********")
                .append("teamToken", "********")
                .append("notifyTeam", notifyTeam)
                .append("buildNotes", buildNotes)
                .append("file", file)
                .append("dsymFile", dsymFile)
                .append("lists", lists)
                .append("replace", replace)
                .append("proxyHost", proxyHost)
                .append("proxyUser", proxyUser)
                .append("proxyPass", "********")
                .append("proxyPort", proxyPort)
                .append("debug", debug)
                .toString();
        }
    }

    private Logger logger = null;

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    public Map upload(UploadRequest ur) throws IOException, org.json.simple.parser.ParseException {
        boolean tmpIsAndroid = ur.file.getName().endsWith(".apk"); // API is agnostic but currently has android specific issues

        DefaultHttpClient httpClient = new DefaultHttpClient();

        // Configure the proxy if necessary
        if(ur.proxyHost!=null && !ur.proxyHost.isEmpty() && ur.proxyPort>0) {
            Credentials cred = null;
            if(ur.proxyUser!=null && !ur.proxyUser.isEmpty())
                cred = new UsernamePasswordCredentials(ur.proxyUser, ur.proxyPass);

            httpClient.getCredentialsProvider().setCredentials(new AuthScope(ur.proxyHost, ur.proxyPort),cred);
            HttpHost proxy = new HttpHost(ur.proxyHost, ur.proxyPort);
            httpClient.getParams().setParameter( ConnRoutePNames.DEFAULT_PROXY, proxy);
        }

        HttpHost targetHost = new HttpHost("testflightapp.com");
        HttpPost httpPost = new HttpPost("/api/builds.json");
        FileBody fileBody = new FileBody(ur.file);

        MultipartEntity entity = new MultipartEntity();
        entity.addPart("api_token", new StringBody(ur.apiToken));
        entity.addPart("team_token", new StringBody(ur.teamToken));
        entity.addPart("notes", new StringBody(ur.buildNotes, "text/plain",Charset.forName("UTF-8")));
        entity.addPart("file", fileBody);

        if (ur.dsymFile != null) {
            FileBody dsymFileBody = new FileBody(ur.dsymFile);
            entity.addPart("dsym", dsymFileBody);
        }

        if (ur.lists.length() > 0)
            entity.addPart("distribution_lists", new StringBody(ur.lists));
        entity.addPart("notify", new StringBody(ur.notifyTeam ? "True" : "False"));
        if (!tmpIsAndroid && ur.replace) 
            entity.addPart("replace", new StringBody("True"));
        httpPost.setEntity(entity);

        logDebug("POST Request: " + ur);

        HttpResponse response = httpClient.execute(targetHost,httpPost);
        HttpEntity resEntity = response.getEntity();

        InputStream is = resEntity.getContent();

        // Improved error handling.
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != 200) {
            String responseBody = new Scanner(is).useDelimiter("\\A").next();
            throw new UploadException(statusCode, responseBody, response);
        }

        StringWriter writer = new StringWriter();
        IOUtils.copy(is, writer, "UTF-8");
        String json = writer.toString();

        logDebug("POST Answer: " + json);

        JSONParser parser = new JSONParser();

        return (Map)parser.parse(json);
    }

    private void logDebug(String message) {
        if (logger != null) {
            logger.logDebug(message);
        }
    }
}
