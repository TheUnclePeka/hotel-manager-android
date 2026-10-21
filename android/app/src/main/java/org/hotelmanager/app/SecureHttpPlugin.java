package org.hotelmanager.app;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

@CapacitorPlugin(name="SecureHttp")
public class SecureHttpPlugin extends Plugin {
    private final java.util.concurrent.ExecutorService requests=java.util.concurrent.Executors.newFixedThreadPool(4);
    @Override protected void handleOnDestroy(){requests.shutdownNow();}
    @PluginMethod public void trust(PluginCall call) {
        try {TrustedConnection.trust(getContext(),new URL(call.getString("url","")),call.getString("fingerprint",""));call.resolve();}
        catch(Exception e){call.reject("Не удалось сохранить доверие серверу");}
    }
    @PluginMethod public void pin(PluginCall call) {
        try {JSObject result=new JSObject();result.put("fingerprint",TrustedConnection.pin(getContext(),new URL(call.getString("url",""))));call.resolve(result);}
        catch(Exception e){call.reject("Некорректный адрес сервера");}
    }
    @PluginMethod public void request(PluginCall call) {
        requests.execute(()->{
            HttpsURLConnection connection=null;
            try {
                connection=TrustedConnection.open(getContext(),new URL(call.getString("url","")));
                String method=call.getString("method","GET");
                if(!java.util.Set.of("GET","POST","PUT","DELETE").contains(method)) throw new Exception("Unsupported method");
                connection.setRequestMethod(method);
                String token=call.getString("token","");
                if(!token.isEmpty()) {
                    if(!token.matches("[A-Za-z0-9_-]{43}")) throw new Exception("Invalid token");
                    connection.setRequestProperty("Authorization","Bearer "+token);
                }
                String body=call.getString("body");
                if(body!=null) {
                    byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
                    if(bytes.length>10*1024*1024) throw new Exception("Request too large");
                    connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");
                    connection.setFixedLengthStreamingMode(bytes.length);
                    try(java.io.OutputStream out=connection.getOutputStream()){out.write(bytes);}
                }
                int status=connection.getResponseCode();
                if(status>=300&&status<400) throw new Exception("Redirect refused");
                byte[] data;
                try(java.io.InputStream in=status>=400?connection.getErrorStream():connection.getInputStream()) {
                    data=in==null?new byte[0]:in.readNBytes(32*1024*1024+1);
                    if(data.length>32*1024*1024) throw new Exception("Response too large");
                }
                JSObject result=new JSObject();result.put("status",status);
                result.put("contentType",connection.getContentType());
                result.put("base64",android.util.Base64.encodeToString(data,android.util.Base64.NO_WRAP));call.resolve(result);
            }catch(Exception e){call.reject("Не удалось проверить HTTPS-соединение или получить ответ сервера");}
            finally {if(connection!=null)connection.disconnect();}
        });
    }
}
