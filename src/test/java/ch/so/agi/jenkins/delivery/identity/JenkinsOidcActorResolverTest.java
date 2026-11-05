package ch.so.agi.jenkins.delivery.identity;
import ch.so.agi.jenkins.delivery.client.*;
import java.util.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class JenkinsOidcActorResolverTest {
    static String token(String sub,String issuer,String aud,long expiry){return "a."+Base64.getUrlEncoder().withoutPadding().encodeToString(Wire.JSON.writeValueAsBytes(Map.of("sub",sub,"iss",issuer,"aud",List.of(aud),"typ","Bearer","azp","jenkins-delivery","exp",expiry,"name","Same Name")))+".b";}
    @Test void localBindingDoesNotUseDisplayNameAndRejectsExpiredAudienceOrSubject(){
        String issuer="http://localhost:18080/realms/dateninfrastruktur",sub=UUID.randomUUID().toString();long now=Instant.now().getEpochSecond();
        assertDoesNotThrow(()->JenkinsOidcActorResolver.requireSubjectMatch(token(sub,issuer,"di-ingestion-user",now+120),sub,issuer));
        for(String bad:List.of(token(UUID.randomUUID().toString(),issuer,"di-ingestion-user",now+120),token(sub,issuer,"management-api",now+120),token(sub,issuer,"di-ingestion-user",now-1),token(sub,issuer+"-other","di-ingestion-user",now+120)))assertEquals(401,assertThrows(DeliveryProblem.class,()->JenkinsOidcActorResolver.requireSubjectMatch(bad,sub,issuer)).status());
    }
}
