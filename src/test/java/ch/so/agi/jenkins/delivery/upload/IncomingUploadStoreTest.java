package ch.so.agi.jenkins.delivery.upload;
import ch.so.agi.jenkins.delivery.client.*;
import hudson.FilePath;
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
    @Test void sealedBytesArePrivateStableAndRehashed()throws Exception{
        var store=new IncomingUploadStore(home);var id=UUID.randomUUID();String csv="Jahrgang;Total\n2000;42\n";var evidence=store.receive(id,64,input(csv,"age.csv"));
        var file=home.resolve("data-infrastructure/"+evidence.privateSpoolReference());assertArrayEquals(bytes(csv),Files.readAllBytes(file));assertEquals(Wire.sha(bytes(csv)),evidence.sha256());assertEquals(evidence,store.receive(id,64,input(csv,"age.csv")));
        assertEquals("INPUT_MISMATCH",assertThrows(DeliveryProblem.class,()->store.receive(id,64,input("Jahrgang;Total\n2001;42\n","age.csv"))).code());assertArrayEquals(bytes(csv),Files.readAllBytes(file));
        var target=new FilePath(home.resolve("verified.csv").toFile());store.copyToRun(id,evidence.size(),evidence.sha256(),evidence.privateSpoolReference(),target);assertArrayEquals(bytes(csv),Files.readAllBytes(home.resolve("verified.csv")));
        Files.writeString(file,"Jahrgang;Total\n2001;42\n");var swapped=new FilePath(home.resolve("rejected.csv").toFile());assertThrows(DeliveryProblem.class,()->store.copyToRun(id,evidence.size(),evidence.sha256(),evidence.privateSpoolReference(),swapped));assertFalse(swapped.exists());
    }
    @Test void limitsMalformedNamesAndSymlinksCannotPublish()throws Exception{
        var store=new IncomingUploadStore(home);for(String name:List.of("../data.csv","c:\\data.csv","null\u0000.csv","data.xlsx"))assertThrows(DeliveryProblem.class,()->store.receive(UUID.randomUUID(),64,input("a\n",name)));
        assertEquals(413,assertThrows(DeliveryProblem.class,()->store.receive(UUID.randomUUID(),3,input("a\nbbbb\n","data.csv"))).status());assertThrows(DeliveryProblem.class,()->store.receive(UUID.randomUUID(),64,input("PK\u0003\u0004","data.csv")));assertThrows(DeliveryProblem.class,()->store.receive(UUID.randomUUID(),64,input("<?xml ...","data.csv")));
        Path root=home.resolve("data-infrastructure/uploads"),other=Files.createDirectory(home.resolve("outside"));UUID id=UUID.randomUUID();Files.createSymbolicLink(root.resolve(id.toString()),other);
        assertThrows(DeliveryProblem.class,()->store.receive(id,64,input("a\nb\n","data.csv")));assertEquals(0,Objects.requireNonNull(other.toFile().list()).length);
    }
    @Test void interruptedMaterializationAndTerminalSweepStayBound()throws Exception{
        var store=new IncomingUploadStore(home);UUID id=UUID.randomUUID();
        var evidence=store.receive(id,64,input("PK;Total\n1;2\n","data.csv"));
        Path file=home.resolve("data-infrastructure/"+evidence.privateSpoolReference()),dir=file.getParent();
        Files.delete(file); // Persisted intent, interrupted before atomically moving the input.
        assertThrows(DeliveryProblem.class,()->store.receive(id,64,input("PK;Total\n1;3\n","data.csv")));
        assertEquals(evidence,store.receive(id,64,input("PK;Total\n1;2\n","data.csv")));
        var unrelated=Files.writeString(dir.resolve("foreign.csv"),"retain");
        Files.setLastModifiedTime(dir,java.nio.file.attribute.FileTime.from(java.time.Instant.now().minusSeconds(90000)));
        assertEquals(List.of(id),store.maintenanceBatch(""));
        store.maintain(id,()->false);assertTrue(Files.exists(file));
        store.maintain(id,()->true);assertFalse(Files.exists(file));assertEquals("retain",Files.readString(unrelated));
        assertTrue(store.maintenanceBatch("").isEmpty());
        assertThrows(DeliveryProblem.class,()->store.receive(UUID.randomUUID(),64,input("\uFEFF<?xml ...","data.csv")));
    }
    @Test void twoStreamsCannotOwnTheSameSubmission()throws Exception{
        var store=new IncomingUploadStore(home);UUID id=UUID.randomUUID();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var first=pool.submit(()->store.receive(id,64,(p,limit)->{entered.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new IOException("Test timeout");}catch(InterruptedException e){throw new IOException(e);}return input("a\nb\n","data.csv").read(p,limit);}));
            assertTrue(entered.await(10,TimeUnit.SECONDS));try{assertEquals(409,assertThrows(DeliveryProblem.class,()->store.receive(id,64,(p,limit)->{throw new AssertionError("Second stream consumed");})).status());}finally{release.countDown();}
            assertEquals(Wire.sha(bytes("a\nb\n")),first.get(10,TimeUnit.SECONDS).sha256());
        }
    }
}
