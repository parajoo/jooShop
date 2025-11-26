package cn.wolfcode.test;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class hashDecodeNacos {
    public static void main(String[] args) {
        // 你的新密码
        String newPassword = "zmin";

        // 使用BCryptPasswordEncoder生成哈希密码
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        String encodedPassword = passwordEncoder.encode(newPassword);

        // 输出生成的哈希密码
        System.out.println("Encoded Password: " + encodedPassword);
    }
}