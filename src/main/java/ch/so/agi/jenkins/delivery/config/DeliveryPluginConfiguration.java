package ch.so.agi.jenkins.delivery.config;

import hudson.Extension;
import jenkins.model.GlobalConfiguration;
import java.net.URI;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;

/** Operator-only JCasC values; all endpoints and credential names are fixed for this stack. */
@Extension @Symbol("diDelivery")
public final class DeliveryPluginConfiguration extends GlobalConfiguration {
    public static final long DEFAULT_UPLOAD=104857600L,HARD_UPLOAD=268435456L;
    public static final String PLUGIN_CREDENTIAL="di-ingestion-plugin",SERVICE_CREDENTIAL="di-ingestion-executor-client";
    private String backendBaseUri,issuer,publicOrigin,instance;
    private long maxUploadBytes=DEFAULT_UPLOAD;
    public DeliveryPluginConfiguration(){load();}
    public static DeliveryPluginConfiguration get(){return GlobalConfiguration.all().get(DeliveryPluginConfiguration.class);}
    @DataBoundSetter public void setBackendBaseUri(String value){backendBaseUri=value;}
    @DataBoundSetter public void setIssuer(String value){issuer=value;}
    @DataBoundSetter public void setPublicOrigin(String value){publicOrigin=value;}
    @DataBoundSetter public void setInstance(String value){instance=value;}
    @DataBoundSetter public void setMaxUploadBytes(long value){maxUploadBytes=value;}
    public String getBackendBaseUri(){return backendBaseUri;}
    public String getIssuer(){return issuer;}
    public String getPublicOrigin(){return publicOrigin;}
    public String getInstance(){return instance;}
    public long getMaxUploadBytes(){return maxUploadBytes;}
    public boolean configured(){return backendBaseUri!=null;}
    public void validate(){
        if(!"http://admin:8080/internal/ingestion/".equals(backendBaseUri) || instance==null || !instance.matches("[0-9a-f]{32}") || maxUploadBytes<1 || maxUploadBytes>HARD_UPLOAD)throw new IllegalStateException("Invalid explicit delivery backend binding");
        if(!origin(issuer,"/realms/dateninfrastruktur") || !origin(publicOrigin,""))throw new IllegalStateException("Invalid delivery issuer or public origin");
    }
    private static boolean origin(String value,String path){
        if(value==null)return false;
        try{var uri=URI.create(value);return uri.getHost()!=null && ("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && "localhost".equals(uri.getHost()) && uri.getPort()>0)) && uri.getRawUserInfo()==null && uri.getRawQuery()==null && uri.getRawFragment()==null && path.equals(uri.getRawPath());}
        catch(IllegalArgumentException e){return false;}
    }
    public URI backendBaseUri(){validate();return URI.create(backendBaseUri);}
    public URI serviceTokenUri(){validate();return URI.create("http://keycloak:8080/realms/dateninfrastruktur/protocol/openid-connect/token");}
}
