import com.hakimi.aviation.entity.User;
import com.hakimi.aviation.util.JWTUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Generates synthetic JWT test users using the same JWTUtils and signing key as the running app. */
public class GenerateJMeterUsers {
    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            throw new IllegalArgumentException("Usage: GenerateJMeterUsers <firstUserId> <count> <flightIdA> <flightIdB> <outputCsv>");
        }

        long firstUserId = Long.parseLong(args[0]);
        int count = Integer.parseInt(args[1]);
        long flightIdA = Long.parseLong(args[2]);
        long flightIdB = Long.parseLong(args[3]);
        Path outputCsv = Path.of(args[4]);
        List<String> rows = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            long userId = firstUserId + i;
            User user = new User();
            user.setId(userId);
            user.setUserName("JMeterStress_" + userId);
            user.setAvatar("JMeterTest");
            String token = JWTUtils.generateJsonWebToken(user);
            long flightId = (i % 2 == 0) ? flightIdA : flightIdB;
            rows.add(userId + "," + token + "," + flightId);
        }

        Files.createDirectories(outputCsv.toAbsolutePath().getParent());
        Files.write(outputCsv, rows, StandardCharsets.UTF_8);
        System.out.println("Generated " + rows.size() + " synthetic JMeter users.");
    }
}
