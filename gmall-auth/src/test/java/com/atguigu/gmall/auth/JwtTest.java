package com.atguigu.gmall.auth;

import com.atguigu.gmall.common.utils.JwtUtils;
import com.atguigu.gmall.common.utils.RsaUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

public class JwtTest {

    // 别忘了创建D:\\project\rsa目录
	private static final String pubKeyPath = "D:\\project-0316\\rsa\\rsa.pub";
    private static final String priKeyPath = "D:\\project-0316\\rsa\\rsa.pri";

    private PublicKey publicKey;

    private PrivateKey privateKey;

    @Test
    public void testRsa() throws Exception {
        RsaUtils.generateKey(pubKeyPath, priKeyPath, "234");
    }

    @BeforeEach
    public void testGetRsa() throws Exception {
        this.publicKey = RsaUtils.getPublicKey(pubKeyPath);
        this.privateKey = RsaUtils.getPrivateKey(priKeyPath);
    }

    @Test
    public void testGenerateToken() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("id", "11");
        map.put("username", "liuyan");
        // 生成token
        String token = JwtUtils.generateToken(map, privateKey, 1);
        System.out.println("token = " + token);
    }

    @Test
    public void testParseToken() throws Exception {
        String token = "eyJhbGciOiJSUzI1NiJ9.eyJpZCI6IjExIiwidXNlcm5hbWUiOiJsaXV5YW4iLCJleHAiOjE1OTk0NjAxMDF9.d1qrcXJ143eicMfjhnRGfoTBkK6THq4MCgdG8IQ-t-E4sDlUnQ9-fbLibIIZZpvJ8f0gzguCpheUPmZ-_aHxoIRe6j9XdZyqoeV4wlGuwAnzQKFfF1frqR0cXCneCK2bfZRvL_49YpaO80kyM6kswGVLxRC_quHNQyMnbdQy3Zho-pddP66rPexvslCiVIqgjGSwjv7HLs3fJxCqKwY_jIUY5mno4tCtMfFTMDKLALbyJeqw9uAVGcX7pSS5u4i1OM_BCOxfpyFwwilLxKcpA4mUcyI63Web4Ryt1fdEwNzJ6VMCTHZrWTu4nVwwkWw21A1U8KH7i9FkoiWuKlly4Q";

        // 解析token
        Map<String, Object> map = JwtUtils.getInfoFromToken(token, publicKey);
        System.out.println("id: " + map.get("id"));
        System.out.println("userName: " + map.get("username"));
    }
}
