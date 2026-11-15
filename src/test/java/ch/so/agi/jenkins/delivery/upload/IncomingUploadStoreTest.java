package ch.so.agi.jenkins.delivery.upload;
import ch.so.agi.jenkins.delivery.client.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class IncomingUploadStoreTest {
    @TempDir Path home;
    byte[] bytes(String value){return value.getBytes(StandardCharsets.UTF_8);}
    IncomingUploadStore.UploadStream input(String content,String name){return (p,limit)->IncomingUploadStore.receiveBounded(new ByteArrayInputStream(bytes(content)),p,limit,name);}
    String hash(String text) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes(text)));
    }
    @Test void uploadBytesArePrivateAndStable() throws Exception {
        var store = new IncomingUploadStore(home);
        var id = UUID.randomUUID();
        String csv = "Jahrgang;Total\n2000;42\n";
        var evidence = store.receive(id, 64, input(csv, "age.csv"));
        var dir = store.existing(id);
        var file = dir.resolve("input.csv");
        assertArrayEquals(bytes(csv), Files.readAllBytes(file));
        assertEquals(hash(csv), evidence.sha256());
        assertEquals("rwx------", java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(dir)));
        assertEquals("rw-------", java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(file)));
        assertEquals(409, assertThrows(DeliveryProblem.class, () -> store.receive(id, 64, input(csv, "age.csv"))).status());
        store.remove(id);
        assertFalse(Files.exists(dir));
    }
    @Test void limitsMalformedNamesAndSymlinksCannotPublish()throws Exception{
        var store=new IncomingUploadStore(home);for(String name:List.of("../data.csv","c:\\data.csv","null\u0000.csv","data.xlsx"))assertThrows(DeliveryProblem.class,()->store.receive(UUID.randomUUID(),64,input("a\n",name)));
        assertEquals(413,assertThrows(DeliveryProblem.class,()->store.receive(UUID.randomUUID(),3,input("a\nbbbb\n","data.csv"))).status());assertThrows(DeliveryProblem.class,()->store.receive(UUID.randomUUID(),64,input("PK\u0003\u0004","data.csv")));assertThrows(DeliveryProblem.class,()->store.receive(UUID.randomUUID(),64,input("<?xml ...","data.csv")));
        Path root=home.resolve("data-infrastructure/uploads"),other=Files.createDirectory(home.resolve("outside"));UUID id=UUID.randomUUID();Files.createSymbolicLink(root.resolve(id.toString()),other);
        assertThrows(DeliveryProblem.class,()->store.receive(id,64,input("a\nb\n","data.csv")));assertEquals(0,Objects.requireNonNull(other.toFile().list()).length);
    }
    @Test void interruptedUploadRemovesItsPrivateDirectory() throws Exception {
        var store = new IncomingUploadStore(home);
        var id = UUID.randomUUID();
        assertThrows(IOException.class, () -> store.receive(id, 64, (p, limit) -> {
            Files.writeString(p, "partial");
            throw new IOException("fixture");
        }));
        assertFalse(Files.exists(home.resolve("data-infrastructure/uploads").resolve(id.toString())));
        store.receive(id, 64, input("a\nb\n", "data.csv"));
        assertTrue(Files.exists(store.existing(id).resolve("input.csv")));
    }
    @Test void rejectsPaddedArchivesAndXmlWithoutRejectingCsvPrefixes()throws Exception{
        var store=new IncomingUploadStore(home);
        for(String padding:List.of("", "  \r\n\t", "\uFEFF   "," ".repeat(65534))){
            for(String signature:List.of("PK\u0003\u0004", "<?xml version='1.0'?>")){
                var error=assertThrows(DeliveryProblem.class,()->store.receive(UUID.randomUUID(),70000,input(padding+signature,"data.csv")));
                assertEquals("INVALID_CSV_CONTENT",error.code());
            }
        }
        byte[] gzip={32,9,31,(byte)139,1};
        assertEquals("INVALID_CSV_CONTENT",assertThrows(DeliveryProblem.class,()->store.receive(UUID.randomUUID(),64,(p,n)->IncomingUploadStore.receiveBounded(new ByteArrayInputStream(gzip),p,n,"data.csv"))).code());
        var valid=" \tPK;Total\n1;2\n";assertEquals(hash(valid),store.receive(UUID.randomUUID(),64,input(valid,"data.csv")).sha256());
    }
    @Test void twoStreamsCannotOwnTheSameSubmission()throws Exception{
        var store=new IncomingUploadStore(home);UUID id=UUID.randomUUID();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var first=pool.submit(()->store.receive(id,64,(p,limit)->{entered.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new IOException("Test timeout");}catch(InterruptedException e){throw new IOException(e);}return input("a\nb\n","data.csv").read(p,limit);}));
            assertTrue(entered.await(10,TimeUnit.SECONDS));try{assertEquals(409,assertThrows(DeliveryProblem.class,()->store.receive(id,64,(p,limit)->{throw new AssertionError("Second stream consumed");})).status());}finally{release.countDown();}
            assertEquals(hash("a\nb\n"),first.get(10,TimeUnit.SECONDS).sha256());
        }
    }
}
