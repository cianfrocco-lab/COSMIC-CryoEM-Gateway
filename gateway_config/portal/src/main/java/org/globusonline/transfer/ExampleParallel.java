package org.globusonline.transfer;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ExampleParallel {
    private JSONTransferAPIClient client;
    private final String endpoint;
    private final String path;
    private static DateFormat isoDateFormat =
                            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

    public ExampleParallel(JSONTransferAPIClient client, String endpoint, String path) {
        this.client = client;
        this.endpoint = endpoint;
        this.path = path;
    }

    public static void main(String args[]) {
    	
//    	grith.jgrith.Environment.initEnvironment();
    	
        if (args.length < 1) {
            System.err.println(
                "Usage: java org.globusonline.transfer.Example "
                + "username endpoint path [cafile certfile keyfile [baseurl]]]");
            System.exit(1);
        }
        String username = args[0];
        
        String endpoint = args[1];
        
        String path = args[2];

        String cafile = null;
        if (args.length > 3 && args[3].length() > 0)
            cafile = args[3];

        String certfile = null;
        if (args.length > 4 && args[4].length() > 0)
            certfile = args[4];

        String keyfile = null;
        if (args.length > 5 && args[5].length() > 0)
            keyfile = args[5];

        String baseUrl = null;
        if (args.length > 6 && args[6].length() > 0)
            baseUrl = args[6];
        

        try {
            JSONTransferAPIClient c = new JSONTransferAPIClient(username,
                                         cafile, certfile, keyfile, baseUrl);
            System.out.println("base url: " + c.getBaseUrl());
            
            c.setUseMultiThreaded(true);
            
            ExampleParallel e = new ExampleParallel(c, endpoint, path);
            e.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void run()
    throws IOException, JSONException, GeneralSecurityException, APIError {
        JSONTransferAPIClient.Result r;
        
        int threads = 20;
		Long start = new Date().getTime();
        
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        for ( int i=0; i<threads; i++) {
        	
        	final int run = i;
        	
        	Thread t = new Thread() {
        		public void run() {
        			
        			System.out.println("Listing started, run: "+run);
        			try {
        				
        		        try {
        					Thread.sleep((int)(Math.random()*5000));
        				} catch (InterruptedException e) {
        					e.printStackTrace();
        				}

        			

        	        Map<String, String> params = new HashMap<String, String>();
        	        if (path != null) {
        	            params.put("path", path);
        	        }
        	        String resource = BaseTransferAPIClient.endpointPath(endpoint)
        	                          + "/ls";
        	        JSONTransferAPIClient.Result r = client.getResult(resource, params);
        	        
        	        System.out.println("Listing finished, run: "+run);
        			} catch (Exception e) {
        				e.printStackTrace();
        			}

        		}
        	};
        	executor.execute(t);
        }
        
        executor.shutdown();
        
        try {
			executor.awaitTermination(10, TimeUnit.HOURS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		Long end = new Date().getTime();
		
		System.out.println("TIME: "+(end-start)/1000);

    }

}
