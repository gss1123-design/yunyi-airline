package com.hakimi.aviation;

import com.hakimi.aviation.entity.User;
import com.hakimi.aviation.util.JWTUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import java.io.FileWriter;
import java.io.IOException;

@SpringBootTest
public class JMeterDataGenerator {

    @Test
    public void generateCsvData() throws IOException {
        int totalUsers = 1000;
        String filePath = "src/test/resources/jmeter_users_data.csv";

        try (FileWriter writer = new FileWriter(filePath)) {
            for (int i = 1; i <= totalUsers; i++) {
                Long mockUserId = (long) (1000 + i);

                // 1. 临时捏造一个合法的 User 对象
                User mockUser = new User();
                mockUser.setId(mockUserId);
                mockUser.setUserName("StressTest_" + mockUserId);
                mockUser.setAvatar("mockUserAvatar");

                // 2. 传入 User 对象生成真实的 Token
                String token = JWTUtils.generateJsonWebToken(mockUser);

                // 3. 写入 CSV (格式: userId,token)
                writer.write(mockUserId + "," + token + "\n");
            }
            System.out.println("✅ 500条真实压测数据生成完毕！文件路径: " + filePath);
        }
    }
}
