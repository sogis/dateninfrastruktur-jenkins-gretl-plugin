package ch.so.agi.jenkins.delivery.client;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** No redirects, bounded body and timeouts, no token/body logging. */
public final class BoundedHttp {
    private BoundedHttp(){}
    public record Response(int status,byte[] body){@Override public String toString(){return "Response[status="+status+"]";}}
    public static Response request(URI uri,String method,Map<String,String> headers,byte[] body){
        HttpURLConnection c=null;
        try{
            c=(HttpURLConnection)uri.toURL().openConnection(java.net.Proxy.NO_PROXY);c.setInstanceFollowRedirects(false);c.setConnectTimeout(5000);c.setReadTimeout(12000);c.setRequestMethod(method);c.setUseCaches(false);
            for(var h:headers.entrySet())c.setRequestProperty(h.getKey(),h.getValue());
            if(body!=null){if(body.length>65536)throw DeliveryProblem.invalid();c.setDoOutput(true);c.setFixedLengthStreamingMode(body.length);try(var out=c.getOutputStream()){out.write(body);}}
            int status=c.getResponseCode();if(status>=300 && status<400)throw DeliveryProblem.unavailable();
            try(var in=status>=400?c.getErrorStream():c.getInputStream()){
                byte[] bytes=in==null?new byte[0]:in.readNBytes(262145);if(bytes.length>262144)throw DeliveryProblem.unavailable();return new Response(status,bytes);
            }
        }catch(IOException e){throw DeliveryProblem.unavailable();}finally{if(c!=null)c.disconnect();}
    }
    public static byte[] utf8(String value){return value.getBytes(StandardCharsets.UTF_8);}
}
