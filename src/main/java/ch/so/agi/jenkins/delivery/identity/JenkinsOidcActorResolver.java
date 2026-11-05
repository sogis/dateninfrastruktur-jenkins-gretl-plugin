package ch.so.agi.jenkins.delivery.identity;

import ch.so.agi.jenkins.delivery.client.*;
import ch.so.agi.jenkins.delivery.config.DeliveryPluginConfiguration;
import hudson.model.User;
import hudson.security.ACL;
import java.time.Instant;
import java.util.Base64;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.oic.OicCredentials;
import org.jenkinsci.plugins.oic.OicSecurityRealm;
import org.kohsuke.stapler.StaplerRequest2;

/** Browser session only. Admin performs the independent JWT signature/issuer/audience check. */
public final class JenkinsOidcActorResolver {
    public record UserTokenContext(String subject,String accessToken){@Override public String toString(){return "UserTokenContext[private]";}}
    public UserTokenContext current(StaplerRequest2 request){
        var config=DeliveryPluginConfiguration.get();config.validate();var auth=Jenkins.getAuthentication2();
        if(request.getHeader("Authorization")!=null || auth==ACL.SYSTEM2 || !auth.isAuthenticated() || "anonymous".equals(auth.getName()) || !(Jenkins.get().getSecurityRealm() instanceof OicSecurityRealm realm) || !"jenkins-delivery".equals(realm.getClientId()) || !"sub".equals(realm.getUserNameField()))throw DeliveryProblem.login();
        var user=User.current();if(user==null || !user.getId().equals(auth.getName()) || !user.getId().matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))throw DeliveryProblem.login();
        var credentials=user.getProperty(OicCredentials.class);
        if(credentials==null || credentials.getExpiresAtMillis()==null || credentials.getExpiresAtMillis()<=System.currentTimeMillis())throw DeliveryProblem.login();
        String token=credentials.getAccessToken();requireSubjectMatch(token,user.getId(),config.getIssuer());return new UserTokenContext(user.getId(),token);
    }
    static void requireSubjectMatch(String token,String id,String issuer){
        try{
            if(token==null || token.length()>16384)throw DeliveryProblem.login();var sections=token.split("\\.",-1);if(sections.length!=3 || sections[2].isEmpty())throw DeliveryProblem.login();
            var claims=Wire.JSON.readTree(Base64.getUrlDecoder().decode(sections[1]));
            var audience=claims.path("aud");boolean wanted=audience.isString() && "di-ingestion-user".equals(audience.asString());for(var entry:audience)if(entry.isString() && "di-ingestion-user".equals(entry.asString()))wanted=true;
            if(!wanted || !id.equals(claims.path("sub").asString("")) || !issuer.equals(claims.path("iss").asString("")) || !"Bearer".equals(claims.path("typ").asString("")) || !"jenkins-delivery".equals(claims.path("azp").asString("")) || claims.path("exp").asLong(0)<=Instant.now().getEpochSecond())throw DeliveryProblem.login();
        }catch(RuntimeException e){throw DeliveryProblem.login();}
    }
}
